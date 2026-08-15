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
import com.ev.android.feature.bubble.BubbleOverlays
import com.ev.android.feature.launcher.AppLauncher
import com.ev.android.feature.settings.EvSettings
import kotlinx.coroutines.delay

/** Screen ke saath E.V kya kar sakta hai. */
enum class ScreenAction {
    /** "screen pe kya hai" - padh ke samjha do. */
    READ,

    /** "screen translate karo" - jo likha hai use Hinglish me bolo. */
    TRANSLATE,

    /** "screenshot lo" */
    SCREENSHOT,

    /** "screenshot le kar rehan ko bhejo" */
    SHARE_SHOT,

    /** "scroll up", "upar karo" - pichli reel/post. */
    SCROLL_UP,

    /** "scroll down", "neeche karo" - agli reel/post. */
    SCROLL_DOWN,
}

data class ScreenResult(val ok: Boolean, val message: String)

/**
 * Screen wale kaam.
 *
 * Teen alag raste hain, aur teeno ki apni seema hai:
 *
 *  - **Padhna** Accessibility tree se hota hai. Isme wahi aata hai jo screen
 *    reader ko dikhta hai, yaani likha hua text. Photo ke andar ka text ya
 *    video ka content isme nahi aata.
 *  - **Screenshot** Accessibility ke global action se hota hai, jo Android 11
 *    se pehle exist hi nahi karta. Lene se pehle E.V apne overlay chhupata hai,
 *    warna user ke screenshot me bubble aur action bar bhi aa jate hain.
 *  - **Scroll** pehle screen ke scrollable hisse se, aur wo na mile to seedha
 *    ungli ki tarah swipe karke. Reels aur shorts scrollable node nahi hote,
 *    isliye wahan swipe hi kaam karta hai.
 *
 * Screen recording alag cheez hai (MediaProjection), wo isme jaan-boojh ke
 * nahi hai - uske liye har baar system ka apna confirm dialog aata hai.
 */
object ScreenTools {

    /** Screenshot gallery me aane me thoda waqt leta hai - itni baar dekho. */
    private const val SHOT_RETRIES = 6

    /** Do koshishon ke beech ka gap. */
    private const val SHOT_RETRY_MS = 1000L

    /**
     * Ghadi thodi aage-peeche ho sakti hai, isliye itne second peeche tak ki
     * photo bhi "nayi" maani jayegi.
     */
    private const val SHOT_CLOCK_SLACK_SEC = 5L

    /** Itni der E.V ke overlay gayab rehte hain. */
    private const val OVERLAY_HIDE_MS = 4000L

    /** Overlay hatne ke baad ek pal ruk jao, warna wo shot me aa jate hain. */
    private const val OVERLAY_SETTLE_MS = 450L

    private const val MAX_CHARS = 4000

    suspend fun run(
        context: Context,
        action: ScreenAction,
        target: String? = null,
    ): ScreenResult = when (action) {
        ScreenAction.READ -> describe(context, translate = false)
        ScreenAction.TRANSLATE -> describe(context, translate = true)
        ScreenAction.SCREENSHOT -> takeScreenshot(context)
        ScreenAction.SHARE_SHOT -> shotAndShare(context, target)
        ScreenAction.SCROLL_UP -> scroll(forward = false)
        ScreenAction.SCROLL_DOWN -> scroll(forward = true)
    }

    private suspend fun describe(context: Context, translate: Boolean): ScreenResult {
        if (!AccessibilityHelper.isEnabled(context)) {
            return ScreenResult(
                false,
                "Screen padhne ke liye ek baar Accessibility me E.V on karna padega",
            )
        }

        // Bar khuli ho to usi ka text padh lena bewakoofi hai - pehle use hatao.
        if (BubbleOverlays.hideFor(OVERLAY_HIDE_MS)) delay(OVERLAY_SETTLE_MS)

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

    /**
     * Screenshot.
     *
     * Pehle E.V ke apne overlay hatte hain, phir ek pal ka intezaar, phir shot.
     * Ye intezaar zaroori hai - window hatne me ek frame lagta hai aur usi ek
     * frame me bubble shot me aa jata tha.
     */
    private suspend fun takeScreenshot(context: Context): ScreenResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ScreenResult(false, "Is Android version me E.V khud screenshot nahi le sakta")
        }

        if (!AccessibilityHelper.isEnabled(context)) {
            return ScreenResult(false, "Screenshot ke liye Accessibility me E.V on karna padega")
        }

        if (BubbleOverlays.hideFor(OVERLAY_HIDE_MS)) delay(OVERLAY_SETTLE_MS)

        val ok = EvAccessibilityService.performGlobal(
            AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT,
        )

        return if (ok) {
            ScreenResult(true, "Screenshot le liya")
        } else {
            ScreenResult(false, "Screenshot nahi le paya")
        }
    }

    /**
     * Screenshot le kar bhejna.
     *
     * Naam bola ho to seedha WhatsApp khulta hai aur uski "send to" wali search
     * me naam khud likh diya jata hai - bas contact pe tap karna reh jata hai.
     * WhatsApp kisi bahar wali app ko contact chunne nahi deta, isliye ye aakhri
     * tap hatana mumkin nahi hai; imaandari se itna hi ho sakta hai.
     *
     * Screenshot gallery me aane me kuch phones me 4-5 second lag jate hain,
     * isliye ek hi baar dekh ke haar maan lena galat tha - ab baar baar dekhte
     * hain, aur sirf wahi photo lete hain jo command ke baad bani ho (warna
     * koi purani photo bhej dene ka khatra tha).
     */
    private suspend fun shotAndShare(context: Context, target: String?): ScreenResult {
        val since = System.currentTimeMillis() / 1000 - SHOT_CLOCK_SLACK_SEC

        val taken = takeScreenshot(context)
        if (!taken.ok) return taken

        val uri = awaitNewImage(context, since) ?: return ScreenResult(
            false,
            "Screenshot to ho gaya, par gallery me mila nahi \u2014 khud bhej do",
        )

        val name = target?.takeIf { it.isNotBlank() }?.let { EvSettings.resolveAlias(context, it) }

        if (name != null) {
            // WhatsApp ki contact list me naam khud type ho jayega.
            EvAccessibilityService.armTyping(name)

            val toWhatsApp = shareIntent(uri).setPackage("com.whatsapp")
            if (AppLauncher.startIntent(context, toWhatsApp)) {
                return ScreenResult(
                    true,
                    "Screenshot WhatsApp me khol diya \u2014 " + name +
                        " ka naam search me likh diya hai, tap kar do",
                )
            }

            EvAccessibilityService.cancelTyping()
        }

        val chooser = Intent.createChooser(shareIntent(uri), "Screenshot bhejo")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return if (AppLauncher.startIntent(context, chooser)) {
            ScreenResult(true, "Screenshot le liya \u2014 kisko bhejna hai chun lo")
        } else {
            ScreenResult(false, "Bhejne wali screen khul nahi payi")
        }
    }

    /** Nayi photo ka intezaar - har second dekhte rehte hain. */
    private suspend fun awaitNewImage(context: Context, since: Long): Uri? {
        repeat(SHOT_RETRIES) {
            delay(SHOT_RETRY_MS)
            latestImage(context, since)?.let { return it }
        }
        return null
    }

    private fun shareIntent(uri: Uri): Intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Reels/shorts upar-neeche karna.
     *
     * Pehle screen ka scrollable hissa dhoondte hain; na mile to swipe. Video
     * wali feeds me node scrollable nahi hoti, wahan swipe hi chalta hai.
     */
    private fun scroll(forward: Boolean): ScreenResult {
        if (!EvAccessibilityService.isRunning()) {
            return ScreenResult(
                false,
                "Scroll karne ke liye Accessibility me E.V on karna padega",
            )
        }

        return if (EvAccessibilityService.scroll(forward)) {
            ScreenResult(true, if (forward) "Neeche kar diya" else "Upar kar diya")
        } else {
            ScreenResult(false, "Is screen pe scroll nahi ho paya")
        }
    }

    /** [since] (seconds) ke baad bani sabse nayi photo. */
    private fun latestImage(context: Context, since: Long): Uri? = runCatching {
        val projection = arrayOf(MediaStore.Images.Media._ID)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            MediaStore.Images.Media.DATE_ADDED + " >= ?",
            arrayOf(since.toString()),
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
