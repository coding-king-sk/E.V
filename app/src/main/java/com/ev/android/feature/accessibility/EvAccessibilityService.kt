package com.ev.android.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * E.V ka "haath".
 *
 * Android kisi app ko intent se WhatsApp ka Send button dabane nahi deta — wo
 * jaan-boojh ke block hai. Sirf ek AccessibilityService hi screen ka button
 * actually tap kar sakti hai ya text box me likh sakti hai.
 *
 * Do kaam karti hai, aur dono "armed" hone par hi:
 *
 *  1. [armWhatsAppAutoSend] — WhatsApp ka Send button daba deti hai.
 *  2. [armTyping] — jo bhi text box screen pe focus me hai, usme likh deti hai
 *     ("instagram pe type karo hello").
 *
 * Arm kiye bina ye service kuch nahi karti, aur arm hone ke baad bhi sirf 20-25
 * second ki window rehti hai. Kaam hote hi khud ko disarm kar leti hai.
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
     * poore tree me pehla editable box uthate hain — chat apps me wahi message
     * box hota hai.
     */
    private fun typeInto(root: AccessibilityNodeInfo, text: String): Boolean {
        val focused = runCatching {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        }.getOrNull()

        val target = focused?.takeIf { it.isEditable }
            ?: findEditable(root, 0)
            ?: return false

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

    private fun findEditable(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_TREE_DEPTH) return null
        if (node.isEditable && node.isVisibleToUser) return node

        for (index in 0 until node.childCount) {
            findEditable(node.getChild(index), depth + 1)?.let { return it }
        }
        return null
    }

    // ----------------------------------------------------------- whatsapp

    private fun clickSendButton(root: AccessibilityNodeInfo, packageName: String): Boolean {
        // WhatsApp ka send FAB — stable resource id.
        for (id in listOf("$packageName:id/send", "$packageName:id/send_container")) {
            val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull()
            nodes?.forEach { node ->
                if (clickSelfOrParent(node)) return true
            }
        }

        // Agar WhatsApp ne id badal di ho to content-description se dhoondo.
        findByDescription(root, 0)?.let { node ->
            if (clickSelfOrParent(node)) return true
        }

        return false
    }

    private fun findByDescription(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
        if (node == null || depth > MAX_TREE_DEPTH) return null

        val description = node.contentDescription?.toString()?.lowercase()
        if (description != null && SEND_LABELS.any { description.startsWith(it) }) {
            return node
        }

        for (index in 0 until node.childCount) {
            findByDescription(node.getChild(index), depth + 1)?.let { return it }
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

    companion object {
        private const val AUTO_SEND_WINDOW_MS = 20_000L
        private const val TYPING_WINDOW_MS = 25_000L
        private const val MAX_TREE_DEPTH = 30

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val SEND_LABELS = listOf("send", "bhej", "\u092D\u0947\u091C")

        @Volatile
        private var instance: EvAccessibilityService? = null

        @Volatile
        private var autoSendDeadline = 0L

        @Volatile
        private var typingDeadline = 0L

        @Volatile
        private var pendingText: String? = null

        /** True jab service actually chal rahi ho. */
        fun isRunning(): Boolean = instance != null

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

        /** App kholne se just pehle call karo — khulte hi text box bhar jayega. */
        fun armTyping(text: String) {
            pendingText = text
            typingDeadline = SystemClock.elapsedRealtime() + TYPING_WINDOW_MS
        }

        fun cancelTyping() {
            pendingText = null
            typingDeadline = 0L
        }

        /** GLOBAL_ACTION_HOME / BACK / RECENTS / TAKE_SCREENSHOT / LOCK_SCREEN. */
        fun performGlobal(action: Int): Boolean =
            runCatching { instance?.performGlobalAction(action) }.getOrNull() ?: false

        /** Screen pe ek tap — gesture API se. */
        fun tap(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
        }
    }
}
