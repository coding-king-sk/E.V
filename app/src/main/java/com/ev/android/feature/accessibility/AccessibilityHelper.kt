package com.ev.android.feature.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.ev.android.feature.launcher.AppLauncher

object AccessibilityHelper {

    /**
     * Service enabled hai ya nahi.
     *
     * `isRunning()` sirf tab true hota hai jab service bind ho chuki ho, isliye
     * Settings.Secure list bhi padhte hain \u2014 usse turant pata chal jata hai.
     */
    fun isEnabled(context: Context): Boolean {
        if (EvAccessibilityService.isRunning()) return true

        val component = ComponentName(context, EvAccessibilityService::class.java)
        val flattened = component.flattenToString()
        val shortForm = component.flattenToShortString()

        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        return enabled.split(':').any { entry ->
            entry.equals(flattened, ignoreCase = true) ||
                entry.equals(shortForm, ignoreCase = true)
        }
    }

    fun openSettings(context: Context): Boolean =
        AppLauncher.startIntent(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
}
