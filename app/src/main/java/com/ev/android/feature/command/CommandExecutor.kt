package com.ev.android.feature.command

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.accessibility.EvAccessibilityService
import com.ev.android.feature.contacts.ContactsRepository
import com.ev.android.feature.contacts.PhoneNumbers
import com.ev.android.feature.device.DeviceControls
import com.ev.android.feature.launcher.AppLauncher
import com.ev.android.feature.media.YouTubeResolver
import java.net.URLEncoder

sealed interface CommandResult {
    val message: String

    data class Success(override val message: String) : CommandResult
    data class Failure(override val message: String) : CommandResult
}

object CommandExecutor {

    private const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    suspend fun execute(context: Context, command: EvCommand): CommandResult = when (command) {
        is EvCommand.OpenApp -> openApp(context, command.target)
        is EvCommand.PlayMedia -> playMedia(context, command.query, command.target)
        is EvCommand.SearchInApp -> searchInApp(context, command.query, command.target)
        is EvCommand.SendWhatsApp -> sendWhatsApp(context, command.contactName, command.message)
        is EvCommand.Device -> {
            val result = DeviceControls.run(context, command.action)
            if (result.ok) CommandResult.Success(result.message)
            else CommandResult.Failure(result.message)
        }
        is EvCommand.Unknown -> CommandResult.Failure(
            "Samajh nahi aaya: \"${command.raw}\". Try: \"YouTube pe paisa song lagao\""
        )
    }

    private fun openApp(context: Context, target: AppTarget): CommandResult {
        val pkg = target.packageName

        if (pkg != null && AppLauncher.launchPackage(context, pkg)) {
            return CommandResult.Success("${target.label} khul raha hai")
        }

        target.webFallbackUrl?.let { url ->
            if (AppLauncher.startIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                return CommandResult.Success("${target.label} installed nahi hai \u2014 web pe khol diya")
            }
        }

        if (pkg != null && openPlayStore(context, pkg)) {
            return CommandResult.Success("${target.label} installed nahi hai \u2014 Play Store khol diya")
        }

        return CommandResult.Failure("${target.label} open nahi ho paya")
    }

    /**
     * Search + actually play.
     *
     * Step 1 is the important one: MEDIA_PLAY_FROM_SEARCH usually just lands on
     * YouTube's search screen instead of playing, so we resolve the top result's
     * video id ourselves and open a /watch deep link, which always autoplays.
     */
    private suspend fun playMedia(
        context: Context,
        query: String,
        target: AppTarget,
    ): CommandResult {
        val pkg = target.packageName

        if (pkg == null || pkg == YOUTUBE_PACKAGE) {
            val videoId = YouTubeResolver.firstVideoId(query)
            if (videoId != null && openYouTubeVideo(context, videoId)) {
                return CommandResult.Success("YouTube pe \"$query\" chal raha hai")
            }
        }

        if (pkg != null && startPlayFromSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" play ho raha hai")
        }

        if (pkg != null && startInAppSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" search kiya")
        }

        if (startPlayFromSearch(context, query, packageName = null)) {
            return CommandResult.Success("\"$query\" play ho raha hai")
        }

        return openYouTubeResults(context, query, preferPackage = pkg)
    }

    private fun searchInApp(context: Context, query: String, target: AppTarget): CommandResult {
        val pkg = target.packageName
        if (pkg != null && startInAppSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" search kiya")
        }
        return openYouTubeResults(context, query, preferPackage = pkg)
    }

    /**
     * Right chat kholta hai message already typed ke saath.
     *
     * Agar E.V ki Accessibility service on hai to wo Send button khud daba deti
     * hai \u2014 poora hands-free. Warna user ko ek tap karna padta hai.
     */
    private suspend fun sendWhatsApp(
        context: Context,
        contactName: String?,
        message: String,
    ): CommandResult {
        if (contactName.isNullOrBlank()) {
            return openApp(context, CommandParser.whatsapp)
        }

        val number = when {
            PhoneNumbers.looksLikeNumber(contactName) -> PhoneNumbers.normalize(contactName)
            else -> ContactsRepository.findByName(context, contactName)?.phone
        }

        if (number == null) {
            return if (!ContactsRepository.hasPermission(context)) {
                CommandResult.Failure("Contacts ki permission chahiye naam se message bhejne ke liye")
            } else {
                CommandResult.Failure("\"$contactName\" naam ka contact nahi mila")
            }
        }

        val autoSend = AccessibilityHelper.isEnabled(context)
        if (autoSend) {
            EvAccessibilityService.armWhatsAppAutoSend()
        }

        val successMessage = if (autoSend) {
            "$contactName ko message bheja jaa raha hai\u2026"
        } else {
            "$contactName ka chat khul gaya \u2014 send dabao (auto-send ke liye Accessibility on karo)"
        }

        val encoded = URLEncoder.encode(message, "UTF-8").replace("+", "%20")

        val deepLink = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("whatsapp://send?phone=$number&text=$encoded"),
        ).setPackage("com.whatsapp")
        if (AppLauncher.startIntent(context, deepLink)) {
            return CommandResult.Success(successMessage)
        }

        val waMe = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=$encoded"))
        if (AppLauncher.startIntent(context, waMe)) {
            return CommandResult.Success(successMessage)
        }

        EvAccessibilityService.cancelAutoSend()
        return CommandResult.Failure("WhatsApp open nahi ho paya")
    }

    private fun openYouTubeVideo(context: Context, videoId: String): Boolean {
        val inApp = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$videoId"),
        ).setPackage(YOUTUBE_PACKAGE)
        if (AppLauncher.startIntent(context, inApp)) return true

        val scheme = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
        if (AppLauncher.startIntent(context, scheme)) return true

        val browser = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.youtube.com/watch?v=$videoId"),
        )
        return AppLauncher.startIntent(context, browser)
    }

    private fun startPlayFromSearch(context: Context, query: String, packageName: String?): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            if (packageName != null) setPackage(packageName)
        }
        return AppLauncher.startIntent(context, intent)
    }

    private fun startInAppSearch(context: Context, query: String, packageName: String): Boolean {
        val intent = Intent(Intent.ACTION_SEARCH).apply {
            setPackage(packageName)
            putExtra(SearchManager.QUERY, query)
            putExtra("query", query)
        }
        return AppLauncher.startIntent(context, intent)
    }

    private fun openYouTubeResults(
        context: Context,
        query: String,
        preferPackage: String?,
    ): CommandResult {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val uri = Uri.parse("https://www.youtube.com/results?search_query=$encoded")

        if (preferPackage != null) {
            val inApp = Intent(Intent.ACTION_VIEW, uri).setPackage(preferPackage)
            if (AppLauncher.startIntent(context, inApp)) {
                return CommandResult.Success("\"$query\" ke results khol diye")
            }
        }

        if (AppLauncher.startIntent(context, Intent(Intent.ACTION_VIEW, uri))) {
            return CommandResult.Success("\"$query\" browser me khol diya")
        }

        return CommandResult.Failure("\"$query\" play nahi ho paya")
    }

    private fun openPlayStore(context: Context, packageName: String): Boolean {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        if (AppLauncher.startIntent(context, market)) return true
        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        return AppLauncher.startIntent(context, web)
    }
}
