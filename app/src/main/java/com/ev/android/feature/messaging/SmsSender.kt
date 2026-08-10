package com.ev.android.feature.messaging

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.ev.android.feature.launcher.AppLauncher

/**
 * SMS \u2014 WhatsApp se aasan case.
 *
 * WhatsApp me auto-send Android block karta hai, lekin SMS ke liye Android
 * seedha API deta hai. Yaani SEND_SMS permission ke saath message sach me
 * apne aap chala jata hai, koi accessibility trick nahi chahiye.
 */
object SmsSender {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /** True agar message actually bhej diya gaya. */
    fun send(context: Context, number: String, message: String): Boolean {
        if (!hasPermission(context)) return false

        return runCatching {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false

            val parts = manager.divideMessage(message)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(number, null, parts, null, null)
            } else {
                manager.sendTextMessage(number, null, message, null, null)
            }
            true
        }.getOrDefault(false)
    }

    /** Permission na ho to kam se kam SMS app me draft khol do. */
    fun openDraft(context: Context, number: String, message: String): Boolean {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + number))
            .putExtra("sms_body", message)
        return AppLauncher.startIntent(context, intent)
    }
}
