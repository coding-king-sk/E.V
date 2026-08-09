package com.ev.android.feature.launcher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface LaunchResult {
    /** The real app / system screen opened. */
    data class Opened(val label: String) : LaunchResult

    /** App not installed, so a web or Play Store page opened instead. */
    data class Fallback(val label: String, val reason: String) : LaunchResult

    /** Nothing on this device could handle the request. */
    data class Failed(val label: String) : LaunchResult
}

object AppLauncher {

    fun launch(context: Context, shortcut: AppShortcut): LaunchResult {
        // 1. System screens (Settings, Camera, ...)
        shortcut.systemAction?.let { action ->
            if (startIntent(context, Intent(action))) {
                return LaunchResult.Opened(shortcut.label)
            }
        }

        // 2. Installed app
        shortcut.packageName?.let { pkg ->
            if (launchPackage(context, pkg)) {
                return LaunchResult.Opened(shortcut.label)
            }
        }

        // 3. Web fallback
        shortcut.webFallbackUrl?.let { url ->
            if (startIntent(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                return LaunchResult.Fallback(shortcut.label, "app installed nahi hai, web pe khol diya")
            }
        }

        // 4. Play Store page
        shortcut.packageName?.let { pkg ->
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            if (startIntent(context, market)) {
                return LaunchResult.Fallback(shortcut.label, "install nahi hai, Play Store khol diya")
            }
            val storeUrl = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
            )
            if (startIntent(context, storeUrl)) {
                return LaunchResult.Fallback(shortcut.label, "install nahi hai, Play Store khol diya")
            }
        }

        return LaunchResult.Failed(shortcut.label)
    }

    /** Opens any installed app by its package name. */
    fun launchPackage(context: Context, packageName: String): Boolean {
        val intent = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }
            .getOrNull() ?: return false
        return startIntent(context, intent)
    }

    /** Safe startActivity \u2014 returns false instead of crashing. */
    fun startIntent(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
