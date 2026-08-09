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
            if (start(context, Intent(action))) {
                return LaunchResult.Opened(shortcut.label)
            }
        }

        // 2. Installed app
        shortcut.packageName?.let { pkg ->
            val launchIntent = runCatching {
                context.packageManager.getLaunchIntentForPackage(pkg)
            }.getOrNull()
            if (launchIntent != null && start(context, launchIntent)) {
                return LaunchResult.Opened(shortcut.label)
            }
        }

        // 3. Web fallback
        shortcut.webFallbackUrl?.let { url ->
            if (start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
                return LaunchResult.Fallback(shortcut.label, "app installed nahi hai, web pe khol diya")
            }
        }

        // 4. Play Store page
        shortcut.packageName?.let { pkg ->
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
            if (start(context, marketIntent)) {
                return LaunchResult.Fallback(shortcut.label, "install nahi hai, Play Store khol diya")
            }
            val storeUrl = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
            )
            if (start(context, storeUrl)) {
                return LaunchResult.Fallback(shortcut.label, "install nahi hai, Play Store khol diya")
            }
        }

        return LaunchResult.Failed(shortcut.label)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
