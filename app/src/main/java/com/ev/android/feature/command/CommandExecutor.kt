package com.ev.android.feature.command

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import com.ev.android.feature.launcher.AppLauncher
import java.net.URLEncoder

sealed interface CommandResult {
    val message: String

    data class Success(override val message: String) : CommandResult
    data class Failure(override val message: String) : CommandResult
}

object CommandExecutor {

    fun execute(context: Context, command: EvCommand): CommandResult = when (command) {
        is EvCommand.OpenApp -> openApp(context, command.target)
        is EvCommand.PlayMedia -> playMedia(context, command.query, command.target)
        is EvCommand.SearchInApp -> searchInApp(context, command.query, command.target)
        is EvCommand.Unknown -> CommandResult.Failure(
            "Samajh nahi aaya: \"${command.raw}\". Try: \"YouTube pe paisa song lagao\""
        )
    }

    private fun openApp(context: Context, target: AppTarget): CommandResult {
        val pkg = target.packageName

        if (pkg != null) {
            val launchIntent = runCatching { context.packageManager.getLaunchIntentForPackage(pkg) }
                .getOrNull()
            if (launchIntent != null && AppLauncher.startIntent(context, launchIntent)) {
                return CommandResult.Success("${target.label} khul raha hai")
            }
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
     * Search + autoplay.
     *
     * MEDIA_PLAY_FROM_SEARCH is the same intent Google Assistant uses. YouTube,
     * YouTube Music, Spotify, Gaana etc. all handle it and start playing the
     * best match straight away \u2014 no extra tap needed.
     */
    private fun playMedia(context: Context, query: String, target: AppTarget): CommandResult {
        val pkg = target.packageName

        // 1. Ask the target app to play it directly.
        if (pkg != null && startPlayFromSearch(context, query, pkg)) {
            return CommandResult.Success("${target.label} pe \"$query\" play ho raha hai")
        }

        // 2. In-app search (YouTube opens results and autoplays the top video
        //    when it comes from a search intent).
        if (pkg != null) {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, query)
                putExtra("query", query)
            }
            if (AppLauncher.startIntent(context, searchIntent)) {
                return CommandResult.Success("${target.label} pe \"$query\" search kiya")
            }
        }

        // 3. Let any music/video app on the phone handle it.
        if (startPlayFromSearch(context, query, packageName = null)) {
            return CommandResult.Success("\"$query\" play ho raha hai")
        }

        // 4. YouTube results page \u2014 inside the app if possible, else browser.
        return openYouTubeResults(context, query, preferPackage = pkg)
    }

    private fun searchInApp(context: Context, query: String, target: AppTarget): CommandResult {
        val pkg = target.packageName

        if (pkg != null) {
            val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage(pkg)
                putExtra(SearchManager.QUERY, query)
                putExtra("query", query)
            }
            if (AppLauncher.startIntent(context, searchIntent)) {
                return CommandResult.Success("${target.label} pe \"$query\" search kiya")
            }
        }

        return openYouTubeResults(context, query, preferPackage = pkg)
    }

    private fun startPlayFromSearch(context: Context, query: String, packageName: String?): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, query)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            if (packageName != null) setPackage(packageName)
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
