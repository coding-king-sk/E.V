package com.ev.android.feature.launcher

import android.provider.MediaStore
import android.provider.Settings

/**
 * One launchable target for E.V.
 *
 * Resolution order when launching:
 * 1. [systemAction] (for built-in screens like Settings / Camera)
 * 2. [packageName] via the installed app's launcher intent
 * 3. [webFallbackUrl] in a browser
 * 4. Play Store page for [packageName]
 */
data class AppShortcut(
    val id: String,
    val label: String,
    val emoji: String,
    val packageName: String? = null,
    val webFallbackUrl: String? = null,
    val systemAction: String? = null,
    /** Extra words (incl. Hinglish) used by the search box. */
    val keywords: List<String> = emptyList(),
) {
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (label.lowercase().contains(q)) return true
        if (packageName?.lowercase()?.contains(q) == true) return true
        return keywords.any { it.lowercase().contains(q) }
    }
}

object AppCatalog {

    val shortcuts: List<AppShortcut> = listOf(
        AppShortcut(
            id = "youtube",
            label = "YouTube",
            emoji = "\uD83D\uDCFA",
            packageName = "com.google.android.youtube",
            webFallbackUrl = "https://www.youtube.com",
            keywords = listOf("youtube kholo", "video", "yt"),
        ),
        AppShortcut(
            id = "whatsapp",
            label = "WhatsApp",
            emoji = "\uD83D\uDCAC",
            packageName = "com.whatsapp",
            webFallbackUrl = "https://web.whatsapp.com",
            keywords = listOf("whatsapp kholo", "chat", "message", "wa"),
        ),
        AppShortcut(
            id = "instagram",
            label = "Instagram",
            emoji = "\uD83D\uDCF7",
            packageName = "com.instagram.android",
            webFallbackUrl = "https://www.instagram.com",
            keywords = listOf("insta", "reels", "ig"),
        ),
        AppShortcut(
            id = "gmail",
            label = "Gmail",
            emoji = "\u2709\uFE0F",
            packageName = "com.google.android.gm",
            webFallbackUrl = "https://mail.google.com",
            keywords = listOf("mail", "email", "inbox"),
        ),
        AppShortcut(
            id = "chrome",
            label = "Chrome",
            emoji = "\uD83C\uDF10",
            packageName = "com.android.chrome",
            webFallbackUrl = "https://www.google.com",
            keywords = listOf("browser", "internet", "search"),
        ),
        AppShortcut(
            id = "maps",
            label = "Maps",
            emoji = "\uD83D\uDDFA\uFE0F",
            packageName = "com.google.android.apps.maps",
            webFallbackUrl = "https://maps.google.com",
            keywords = listOf("map", "navigation", "rasta", "location"),
        ),
        AppShortcut(
            id = "telegram",
            label = "Telegram",
            emoji = "\u2708\uFE0F",
            packageName = "org.telegram.messenger",
            webFallbackUrl = "https://web.telegram.org",
            keywords = listOf("tg", "chat"),
        ),
        AppShortcut(
            id = "spotify",
            label = "Spotify",
            emoji = "\uD83C\uDFB5",
            packageName = "com.spotify.music",
            webFallbackUrl = "https://open.spotify.com",
            keywords = listOf("music", "gaana", "song", "play music"),
        ),
        AppShortcut(
            id = "phonepe",
            label = "PhonePe",
            emoji = "\uD83D\uDCB8",
            packageName = "com.phonepe.app",
            keywords = listOf("upi", "payment", "paisa", "pay"),
        ),
        AppShortcut(
            id = "swiggy",
            label = "Swiggy",
            emoji = "\uD83C\uDF54",
            packageName = "in.swiggy.android",
            webFallbackUrl = "https://www.swiggy.com",
            keywords = listOf("food", "khana", "order"),
        ),
        AppShortcut(
            id = "camera",
            label = "Camera",
            emoji = "\uD83D\uDCF8",
            systemAction = MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA,
            keywords = listOf("photo", "tasveer", "click"),
        ),
        AppShortcut(
            id = "settings",
            label = "Settings",
            emoji = "\u2699\uFE0F",
            systemAction = Settings.ACTION_SETTINGS,
            keywords = listOf("setting", "phone settings"),
        ),
        AppShortcut(
            id = "wifi",
            label = "Wi-Fi",
            emoji = "\uD83D\uDCF6",
            systemAction = Settings.ACTION_WIFI_SETTINGS,
            keywords = listOf("wifi", "internet settings"),
        ),
        AppShortcut(
            id = "bluetooth",
            label = "Bluetooth",
            emoji = "\uD83D\uDD35",
            systemAction = Settings.ACTION_BLUETOOTH_SETTINGS,
            keywords = listOf("bt", "pair"),
        ),
    )
}
