package com.ev.android.feature.voice

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Phone on hote hi E.V sunna shuru kar deta hai.
 *
 * Pehle reboot ke baad user ko app kholni padti thi, warna "Hey E.V" bekaar
 * tha \u2014 aur yehi sabse aam shikayat thi, kyunki reboot yaad kaun rakhta hai.
 *
 * Do baatein jaan bujh ke aisi hain:
 *  - Mic ki permission na ho to hum kuch karte hi nahi. Bina permission ke
 *    microphone wala foreground service Android 14+ pe app ko hi gira deta hai.
 *  - Kuch phones (khaas kar chini brands) boot ke baad app ko block kar dete
 *    hain. Aise phone me ye receiver chalega hi nahi \u2014 wahan user ko
 *    Settings me E.V ka "Autostart" khud on karna padega. Ye humare haath me
 *    nahi hai.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        val isBoot = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"

        if (!isBoot) return

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (!micGranted) return

        // Boot ke waqt system par bojh hota hai; service khud ko sambhal leti
        // hai, par crash yahan tak na pahunche isliye chhata laga diya.
        runCatching { EvListeningService.start(context) }
    }
}
