package com.ev.android.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * E.V ka "haath".
 *
 * Android kisi app ko intent se WhatsApp ka Send button dabane nahi deta \u2014 wo
 * jaan-boojh ke block hai. Sirf ek AccessibilityService hi screen ka button
 * actually tap kar sakti hai. Isliye ye service:
 *
 *  1. Sirf tab kaam karti hai jab [armWhatsAppAutoSend] call hua ho (yaani user
 *     ne abhi-abhi E.V se message bhejne ko bola), aur wo bhi 20 second tak.
 *  2. Sirf WhatsApp ke packages sun-ti hai (config me `packageNames`).
 *  3. Send dabate hi khud ko disarm kar deti hai.
 *
 * Iske alawa ye global actions (home / back / screenshot / lock) aur screen tap
 * bhi expose karti hai, jo device controls use karte hain.
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
        if (!isArmed()) return

        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in WHATSAPP_PACKAGES) return

        val root = rootInActiveWindow ?: return
        if (clickSendButton(root, packageName)) {
            autoSendDeadline = 0L
        }
    }

    private fun clickSendButton(root: AccessibilityNodeInfo, packageName: String): Boolean {
        // WhatsApp ka send FAB \u2014 stable resource id.
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
        private const val MAX_TREE_DEPTH = 30

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val SEND_LABELS = listOf("send", "bhej", "\u092D\u0947\u091C")

        @Volatile
        private var instance: EvAccessibilityService? = null

        @Volatile
        private var autoSendDeadline = 0L

        /** True jab service actually chal rahi ho. */
        fun isRunning(): Boolean = instance != null

        private fun isArmed(): Boolean = SystemClock.elapsedRealtime() < autoSendDeadline

        /** WhatsApp deep link kholne se just pehle call karo. */
        fun armWhatsAppAutoSend() {
            autoSendDeadline = SystemClock.elapsedRealtime() + AUTO_SEND_WINDOW_MS
        }

        fun cancelAutoSend() {
            autoSendDeadline = 0L
        }

        /** GLOBAL_ACTION_HOME / BACK / RECENTS / TAKE_SCREENSHOT / LOCK_SCREEN. */
        fun performGlobal(action: Int): Boolean =
            runCatching { instance?.performGlobalAction(action) }.getOrNull() ?: false

        /** Screen pe ek tap \u2014 gesture API se. */
        fun tap(x: Float, y: Float): Boolean {
            val service = instance ?: return false
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 60L)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
        }
    }
}
