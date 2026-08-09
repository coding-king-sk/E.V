package com.ev.android.feature.apps

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A launchable app that is actually installed on this phone. */
data class InstalledApp(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap? = null,
)

object InstalledAppsRepository {

    /**
     * Every app on the phone that has a launcher icon.
     *
     * Works on Android 11+ because the manifest declares a MAIN/LAUNCHER
     * <queries><intent> block, so we do not need QUERY_ALL_PACKAGES.
     */
    suspend fun load(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = runCatching { pm.queryIntentActivities(launcherIntent, 0) }
            .getOrDefault(emptyList())

        resolved.asSequence()
            .mapNotNull { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                InstalledApp(
                    label = runCatching { info.loadLabel(pm).toString() }
                        .getOrDefault(info.packageName),
                    packageName = info.packageName,
                    icon = runCatching {
                        info.loadIcon(pm).toBitmap(width = 96, height = 96).asImageBitmap()
                    }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}
