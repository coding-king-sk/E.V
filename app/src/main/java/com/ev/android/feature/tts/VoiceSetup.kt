package com.ev.android.feature.tts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech

/**
 * Google ki high-quality awaz milti hai ya nahi, aur na mile to user ko ek
 * click me download screen tak pahunchana.
 *
 * Phone ka default TTS engine aksar Samsung/Xiaomi ka apna hota hai, jo
 * robot jaisa bolta hai. Google wala engine natural lagta hai, isliye E.V
 * usi ko pakadta hai.
 */
object VoiceSetup {

    const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"

    fun isGoogleTtsInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(GOOGLE_TTS_PACKAGE, 0)
    }.isSuccess

    /**
     * Seedha "voice data install" screen kholta hai, jahan user bas Install
     * dabata hai. Google engine hi na ho to Play Store bhej dete hain.
     */
    fun openVoiceDataInstall(context: Context): Boolean {
        if (!isGoogleTtsInstalled(context)) return openPlayStore(context)

        val intent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
            setPackage(GOOGLE_TTS_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (start(context, intent)) return true

        // Kuch phones package-specific intent nahi maante.
        val generic = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return start(context, generic) || openTtsSettings(context)
    }

    fun openPlayStore(context: Context): Boolean {
        val market = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=" + GOOGLE_TTS_PACKAGE),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (start(context, market)) return true

        val web = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE_TTS_PACKAGE),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return start(context, web)
    }

    /** Last resort \u2014 system ki Text-to-speech settings. */
    fun openTtsSettings(context: Context): Boolean {
        val intent = Intent("com.android.settings.TTS_SETTINGS")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
