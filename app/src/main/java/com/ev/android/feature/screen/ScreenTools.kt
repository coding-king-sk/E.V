package com.ev.android.feature.screen

import android.accessibilityservice.AccessibilityService
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.accessibility.EvAccessibilityService
import com.ev.android.feature.ai.Conversation
import com.ev.android.feature.launcher.AppLauncher
import kotlinx.coroutines.delay

/** Screen ke saath E.V kya kar sakta hai. */
enum class ScreenAction {
    /** "screen pe kya hai" - padh ke samjha do. */
    READ,

    /** "screen translate karo" - jo likha hai use Hinglish me bolo. */
    TRANSLATE,

    /** "screenshot lo" */
    SCREENSHOT,

    /** "screenshot lekar bhejo" */
    SHARE_SHOT,
}

data class ScreenResult(val ok: Boolean, val message: String)

/**
 * Screen wale kaam.
 *
 * Do alag raste hain, aur dono ki apni seema hai:
 *
 *  - **Padhna** Accessibility tree se hota hai. Isme wahi aata hai jo screen
 *    reader ko dikhta hai, yaani likha hua text. Photo ke andar ka text ya
 *    video ka content isme nahi aata.
 *  - **Screenshot** Accessibility ke global action se hota hai, jo Android 11
 *    se pehle exist hi nahi karta.
 *
 * Screen recording alag cheez hai (MediaProjection), wo isme jaan-boojh ke
 * nahi hai - uske liye har baar system ka apna confirm dialog aata hai.
 */
object ScreenTools {

    /** Screenshot gallery me aane me thoda waqt leta hai. */
    private const val SHOT_SETTLE_MS = 1800L

    private const val MAX_CHARS = 4000

    suspend fun run(context: Context, action: ScreenAction): ScreenResult = when (action) {
        ScreenAction.READ -> describe(context, translate = false)
        ScreenAction.TRANSLATE -> describe(context, translate = true)
        ScreenAction.SCREENSHOT -> takeScreenshot(context)
        ScreenAction.SHARE_SHOT -> shotAndShare(context)
    }

    private suspend fun describe(context: Context, translate: Boolean): ScreenResult {
        if (!AccessibilityHelper.isEnabled(context)) {
            return ScreenResult(
                false,
                "Screen padhne ke liye ek baar Accessibility me E.V on karna padega",
            )
        }

        val text = EvAccessibilityService.screenText(MAX_CHARS)
            ?: return ScreenResult(false, "Screen pe padhne layak kuch mila hi nahi")

        val question = if (translate) {
            "Neeche ek phone screen ka text hai. Ise saaf Hinglish me translate karke sunao, " +
                "chhota rakho:\n\n" + text
        } else {
            "Neeche ek phone screen ka text hai. 2-3 line me Hinglish me batao ki screen pe " +
                "kya hai:\n\n" + text
        }

        val answer = Conversation.answer(context, question)
        if (!answer.isNullOrBlank()) return ScreenResult(true, answer)

        // AI band hai ya key nahi hai - tab bhi khaali haath mat lauto, jo padha
        // wahi suna do.
        val short = text.replace(Regex("\\s+"), " ").take(220)
        return ScreenResult(true, "Screen pe ye likha hai: " + short)
    }

    private fun takeScreenshot(context: Context): ScreenResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ScreenResult(false, "Is Android version me E.V khud screenshot nahi le sakta")
        }

        if (!AccessibilityHelper.isEnabled(context)) {
            return ScreenResult(false, "Screenshot ke liye Accessibility me E.V on karna padega")
        }

        val ok = EvAccessibilityService.performGlobal(
            AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        )

        return if (ok) {
            ScreenResult(true, "Screenshot le liya")
        } else {
            ScreenResult(false, "Screenshot nahi le paya")
        }
    }

    private suspend fun shotAndShare(context: Context): ScreenResult {
        val taken = takeScreenshot(context)
        if (!taken.ok) return taken

        delay(SHOT_SETTLE_MS)

        val uri = latestImage(context) ?: return ScreenResult(
            false,
            "Screenshot to ho gaya, par gallery me mila nahi \u2014 khud bhej do",
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return if (AppLauncher.startIntent(context, Intent.createChooser(send, "Screenshot bhejo"))) {
            ScreenResult(true, "Screenshot le liya \u2014 kisko bhejna hai chun lo")
        } else {
            ScreenResult(false, "Bhejne wali screen khul nahi payi")
        }
    }

    /** Gallery me sabse nayi photo - screenshot lene ke turant baad yahi hoti hai. */
    private fun latestImage(context: Context): Uri? = runCatching {
        val projection = arrayOf(MediaStore.Images.Media._ID)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            MediaStore.Images.Media.DATE_ADDED + " DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(0),
                )
            } else {
                null
            }
        }
    }.getOrNull()
}
