package com.ev.android.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * E.V ka "haath".
 *
 * Android kisi app ko intent se WhatsApp ka Send button dabane nahi deta - wo
 * jaan-boojh ke block hai. Sirf ek AccessibilityService hi screen ka button
 * actually tap kar sakti hai ya text box me likh sakti hai.
 *
 * Teen kaam karti hai:
 *
 *  1. [armWhatsAppAutoSend] - WhatsApp ka Send button daba deti hai.
 *  2. [armTyping] - jo bhi text box screen pe focus me hai, usme likh deti hai
 *     ("instagram pe type karo hello").
 *  3. [typePromptAndSubmit] - browser me khule Gemini/ChatGPT ke box me sawaal
 *     likh ke Submit daba deti hai. Yahi "bina API key wale jawab" ki jad hai.
 *
 * Pehle do kaam "armed" hone par hi hote hain aur 20-25 second ki window ke
 * andar. Teesra kaam [WebLlmBridge] khud step-by-step chalata hai, isliye wo
 * arming ke bharose nahi hai - browser me event kabhi kabhi aate hi nahi.
 *
 * Iske alawa ye screen ko **padh** bhi sakti hai ([screenText]), screen ko
 * upar-neeche [scroll] kar sakti hai, aur ye bhi bata deti hai ki abhi
 * saamne kaun si app khuli hai ([foregroundPackage]).
 */
class EvAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Sabse pehle sirf itna note kar lete hain ki kaun si app saamne hai.
        // Ye sasta kaam hai aur har event pe hota hai, isliye armed check se
        // pehle - warna bubble ko kabhi pata hi nahi chalta ki user Instagram
        // me hai ya YouTube me.
        if (
            event != null &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            val current = event.packageName?.toString()
            if (!current.isNullOrBlank() && current != getPackageName()) {
                lastPackage = current
            }
        }

        val typingArmed = isTypingArmed()
        val sendArmed = isSendArmed()
        if (!typingArmed && !sendArmed) return

        val packageName = event?.packageName?.toString() ?: return
        val root = rootInActiveWindow ?: return

        // Apne hi app me kuch nahi karna.
        if (packageName == getPackageName()) return

        if (typingArmed) {
            val text = pendingText
            if (text != null && typeInto(root, text)) {
                pendingText = null
                typingDeadline = 0L
                return
            }
        }

        if (sendArmed && packageName in WHATSAPP_PACKAGES) {
            if (clickSendButton(root, packageName)) {
                autoSendDeadline = 0L
            }
        }
    }

    // -------------------------------------------------------------- typing

    /**
     * Jo text box abhi focus me hai usme likh do.
     *
     * Pehle focused node dhoondte hain (yahi 90% cases me sahi hota hai), warna
     * poore tree me pehla editable box uthate hain - chat apps me wahi message
     * box hota hai.
     *
     * Ek zaroori pehra: E.V ka apna text box chhod dena hai. Bubble ki action
     * bar bhi ek editable box hai aur wo aksar focus me hoti hai, isliye pehle
     * text wahin gir jata tha - user ki app me kuch nahi likha jata tha.
     */
    private fun typeInto(root: AccessibilityNodeInfo, text: String): Boolean {
        val target = inputBox(root) ?: return false

        // Box focus me na ho to pehle usme click karo, warna kuch apps text
        // set hone ke baad turant hata deti hain.
        if (!target.isFocused) {
            runCatching { target.performAction(AccessibilityNodeInfo.ACTION_FOCUS) }
            runCatching { target.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        }

        val arguments = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text,
            )
        }

        return runCatching {
            target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        }.getOrDefault(false)
    }

    /** Screen pe likhne wala box - pehle focused, warna pehla editable. */
    private fun inputBox(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val focused = runCatching {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()

        val target = focused?.takeIf { it.isEditable && !isOurs(it) }
            ?: findEditable(root, 0)
            ?: return null

        return target.takeIf { !isOurs(it) }
    }

    /** Ye node E.V ka apna hai? (bubble ki action bar) */
    private fun isOurs(node: AccessibilityNodeInfo): Boolean =
        node.packageName?.toString() == getPackageName()

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_TREE_DEPTH) return null
        if (node.isEditable && node.isVisibleToUser && !isOurs(node)) return node

        for (index in 0 until node.childCount) {
            findEditable(node.getChild(index), depth + 1)?.let { return it }
        }
        return null
    }

    // ----------------------------------------------------------- whatsapp

    private fun clickSendButton(root: AccessibilityNodeInfo, packageName: String): Boolean {
        // WhatsApp ka send FAB - stable resource id.
        for (id in listOf("$packageName:id/send", "$packageName:id/send_container")) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
            nodes?.forEach { node ->
                if (clickSelfOrParent(node)) return true
            }
        }

        // Agar WhatsApp ne id badal di ho to content-description se dhoondo.
        findByDescription(root, 0, SEND_LABELS)?.let { node ->
            if (clickSelfOrParent(node)) return true
        }

        return false
    }

    /**
     * Diye hue labels me se kisi se shuru hone wala button.
     *
     * Icon buttons me text nahi hota, unka matlab contentDescription me chhupa
     * hota hai ("Send message", "Submit") - isliye wahi dekhte hain.
     */
    private fun findByDescription(
        node: AccessibilityNodeInfo?,
        depth: Int,
        labels: List<String>,
    ): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_TREE_DEPTH) return null

        val description = node.contentDescription?.toString()?.lowercase()
            ?: node.text?.toString()?.lowercase()

        if (description != null && labels.any { description.startsWith(it) }) {
            return node
        }

        for (index in 0 until node.childCount) {
            findByDescription(node.getChild(index), depth + 1, labels)?.let { return it }
        }
        return null
    }

    private fun clickSelfOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var hops = 0
        while (current != null && hops < 5) {
            if (current.isClickable && current.isEnabled) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            hops++
        }
        return false
    }

    // ----------------------------------------------------------- scrolling

    /**
     * Ungli jaisa swipe.
     *
     * Reels, shorts aur status wali screens me koi scrollable node nahi hota -
     * wahan video ke upar seedha swipe hi chalta hai. Isliye node wala tarika
     * fail hone par yahi aakhri sahara hai.
     */
    private fun swipe(forward: Boolean): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels / 2f
        val high = metrics.heightPixels * 0.75f
        val low = metrics.heightPixels * 0.28f

        val path = Path().apply {
            if (forward) {
                moveTo(x, high)
                lineTo(x, low)
            } else {
                moveTo(x, low)
                lineTo(x, high)
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return runCatching { dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    companion object {
        private const val AUTO_SEND_WINDOW_MS = 20_000L
        private const val TYPING_WINDOW_MS = 25_000L
        private const val MAX_TREE_DEPTH = 30
        private const val SWIPE_MS = 220L

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val SEND_LABELS = listOf("send", "bhej", "\u092D\u0947\u091C")

        /**
         * Gemini, ChatGPT aur baaki chat wali websites ka Submit button.
         *
         * Har site ka apna lafz hai, isliye list lambi hai. "stop" jaan boojh ke
         * nahi hai - jawab likhte waqt wahi button Stop ban jata hai, aur use
         * dabane se jawab beech me ruk jata.
         */
        private val SUBMIT_LABELS = listOf(
            "send message", "send prompt", "submit prompt", "submit", "send",
            "run", "ask", "go", "search",
        )

        @Volatile
        private var instance: EvAccessibilityService? = null

        @Volatile
        private var autoSendDeadline = 0L

        @Volatile
        private var typingDeadline = 0L

        @Volatile
        private var pendingText: String? = null

        @Volatile
        private var lastPackage: String? = null

        /** True jab service actually chal rahi ho. */
        fun isRunning(): Boolean = instance != null

        /**
         * Abhi saamne kaun si app khuli hai.
         *
         * @return null agar service off hai ya abhi tak koi app ka event nahi aaya
         */
        fun foregroundPackage(): String? = lastPackage

        private fun isSendArmed(): Boolean = SystemClock.elapsedRealtime() < autoSendDeadline

        private fun isTypingArmed(): Boolean =
            pendingText != null && SystemClock.elapsedRealtime() < typingDeadline

        /** WhatsApp deep link kholne se just pehle call karo. */
        fun armWhatsAppAutoSend() {
            autoSendDeadline = SystemClock.elapsedRealtime() + AUTO_SEND_WINDOW_MS
        }

        fun cancelAutoSend() {
            autoSendDeadline = 0L
        }

        /** App kholne se just pehle call karo - khulte hi text box bhar jayega. */
        fun armTyping(text: String) {
            pendingText = text
            typingDeadline = SystemClock.elapsedRealtime() + TYPING_WINDOW_MS
        }

        fun cancelTyping() {
            pendingText = null
            typingDeadline = 0L
        }

        // ------------------------------------------------- browser automation

        /**
         * Browser me khule chat box me sawaal likho aur Submit dabao.
         *
         * Ye ek hi baar me poora kaam karne ki koshish **nahi** karta. Page ke
         * load hone, box ke aane aur button ke enable hone me waqt lagta hai,
         * isliye [WebLlmBridge] ise har 800ms me dobara bulata hai aur ye har
         * baar bas agla chhota kadam uthata hai:
         *
         *  1. box mila? -> sawaal likh do
         *  2. sawaal box me dikh raha hai? -> Submit dabao
         *
         * @return true tabhi jab prompt sach me bhej diya gaya ho
         */
        fun typePromptAndSubmit(text: String): Boolean {
            val service = instance ?: return false
            val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return false

            val box = service.inputBox(root) ?: return false
            val current = box.text?.toString().orEmpty()

            // Box me sawaal abhi tak nahi pahuncha - pehle wahi kaam.
            val head = text.take(24)
            if (!current.contains(head, ignoreCase = true)) {
                service.typeInto(root, text)
                return false
            }

            // Sawaal box me hai - ab bhejna hai.
            service.findByDescription(root, 0, SUBMIT_LABELS)?.let { node ->
                if (service.clickSelfOrParent(node)) return true
            }

            // Button na mile to keyboard ka Enter hi sahi.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val entered = runCatching {
                    box.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id,
                    )
                }.getOrDefault(false)
                if (entered) return true
            }

            return false
        }

        /** GLOBAL_ACTION_HOME / BACK / RECENTS / TAKE_SCREENSHOT / LOCK_SCREEN. */
        fun performGlobal(action: Int): Boolean =
            runCatching { instance?.performGlobalAction(action) }.getOrNull() ?: false

        /** Screen pe ek tap - gesture API se. */
        fun tap(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
        }

        /**
         * Screen upar-neeche karo.
         *
         * @param forward true = neeche/agli reel, false = upar/pichli reel
         */
        fun scroll(forward: Boolean): Boolean {
            val service = instance ?: return false

            val action = if (forward) {
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }

            val root = runCatching { service.rootInActiveWindow }.getOrNull()
            val scrollable = if (root != null) findScrollable(root, 0) else null

            if (scrollable != null) {
                val done = runCatching { scrollable.performAction(action) }.getOrDefault(false)
                if (done) return true
            }

            return service.swipe(forward)
        }

        private fun findScrollable(
            node: AccessibilityNodeInfo?,
            depth: Int,
        ): AccessibilityNodeInfo? {
            if (node == null || depth > MAX_TREE_DEPTH) return null
            if (node.isScrollable && node.isVisibleToUser) return node

            for (index in 0 until node.childCount) {
                findScrollable(node.getChild(index), depth + 1)?.let { return it }
            }
            return null
        }

        /**
         * Screen pe abhi jo dikh raha hai uska saara text.
         *
         * Node ka text na ho to contentDescription le lete hain - icons aur
         * buttons ka matlab wahin chhupa hota hai. Ek hi text baar-baar na aaye
         * iska dhyan rakhte hain, warna list wali screens me wahi line 20 baar
         * aa jati hai aur AI ka poora context usi me chala jata hai.
         *
         * @return null agar service chalu nahi hai ya screen pe text hi nahi
         */
        fun screenText(limit: Int = 4000): String? {
            val root = runCatching { instance?.rootInActiveWindow }.getOrNull() ?: return null

            val out = StringBuilder()
            collectText(root, 0, out, limit)

            return out.toString().trim().ifBlank { null }
        }

        private fun collectText(
            node: AccessibilityNodeInfo?,
            depth: Int,
            out: StringBuilder,
            limit: Int,
        ) {
            if (node == null || depth > MAX_TREE_DEPTH || out.length >= limit) return

            if (node.isVisibleToUser) {
                val text = node.text?.toString()?.trim()
                    ?: node.contentDescription?.toString()?.trim()

                if (!text.isNullOrBlank() && !out.contains(text)) {
                    out.append(text).append('\n')
                }
            }

            for (index in 0 until node.childCount) {
                collectText(node.getChild(index), depth + 1, out, limit)
            }
        }
    }
}
