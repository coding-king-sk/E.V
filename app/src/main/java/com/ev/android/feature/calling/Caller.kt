package com.ev.android.feature.calling

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.ev.android.feature.launcher.AppLauncher

object Caller {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Permission ho to seedha call lagti hai (ACTION_CALL).
     * Warna dialer khulta hai number bhara hua (ACTION_DIAL) \u2014 ek tap baaki.
     */
    fun call(context: Context, number: String): Boolean {
        val uri = Uri.parse("tel:" + number)

        if (hasPermission(context)) {
            if (AppLauncher.startIntent(context, Intent(Intent.ACTION_CALL, uri))) return true
        }

        return AppLauncher.startIntent(context, Intent(Intent.ACTION_DIAL, uri))
    }

    fun dialedDirectly(context: Context): Boolean = hasPermission(context)
}
