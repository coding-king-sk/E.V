package com.ev.android.feature.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import com.ev.android.feature.accessibility.AccessibilityHelper
import com.ev.android.feature.accessibility.EvAccessibilityService
import com.ev.android.feature.launcher.AppLauncher
import kotlinx.coroutines.delay
import java.net.URLEncoder

/**
 * Local automation backend - bina API key ke jawab.
 *
 * Poora kaam phone ke andar hota hai, kisi server ke bina:
 *
 *  1. App ke text box se sawaal aata hai
 *  2. Chrome me Gemini (ya ChatGPT) khulta hai
 *  3. Accessibility us page ke box me sawaal likhti hai aur Submit dabati hai
 *  4. Jawab screen pe likha jata hai; hum use padhte rehte hain
 *  5. Jab likhna ruk jaye - matlab jawab poora - to E.V wapas aa jata hai aur
 *     wahi jawab app ki screen pe dikh jata hai
 *
 * **Do sachchai jo chhupani nahi chahiye:**
 *
 *  1. Ye sach me "invisible background" nahi hai. Android kisi app ko chhupi
 *     hui window ka text padhne nahi deta - Accessibility sirf wahi dekh sakti
 *     hai jo screen pe actually dikh raha ho. Isliye Chrome kuch second ke liye
 *     saamne aayega aur kaam hote hi apne aap wapas chala jayega. Bina root ke
 *     iska koi doosra raasta nahi hai.
 *  2. Gemini/ChatGPT me Chrome ka login chahiye. Login na ho, ya unka page
 *     badal jaye, to ye khud [Provider.WEB] pe chala jata hai - wahan login
 *     lagta hi nahi aur sawaal seedha URL me chala jata hai.
 */
object WebLlmBridge {

    /** Kahan se jawab lana hai. */
    enum class Provider {
        /** gemini.google.com - Chrome me Google login chahiye. */
        GEMINI,

        /** chatgpt.com - login ke bina bhi aksar chal jata hai. */
        CHATGPT,

        /** Login-free - sawaal seedha URL me, na typing na submit. */
        WEB,
    }

    private const val PREFS = "ev_web_llm"
    private const val KEY_PROVIDER = "provider"

    private const val CHROME = "com.android.chrome"

    /** Page khulne ka pehla intezaar. */
    private const val PAGE_WAIT_MS = 3_000L

    /** Box dhoondhne aur Submit dabane ki koshishon ke beech ka gap. */
    private const val TYPE_STEP_MS = 800L

    /** Itni der me prompt na bhej paye to doosra raasta pakdo. */
    private const val TYPE_TIMEOUT_MS = 18_000L

    /** Jawab padhne ke do rounds ke beech ka gap. */
    private const val POLL_MS = 1_200L

    /** Jawab ka intezaar itni der tak. */
    private const val ANSWER_TIMEOUT_MS = 45_000L

    /** Itni baar text na badle to maan lete hain ki jawab poora ho gaya. */
    private const val STABLE_ROUNDS = 3

    /** Isse chhota text jawab nahi, page ka loading hai. */
    private const val MIN_ANSWER_CHARS = 60

    /** Screen pe dikhane/sunane layak lambai. */
    private const val MAX_REPLY_CHARS = 900

    private const val SCREEN_LIMIT = 8_000

    /**
     * Browser ki apni cheezein - jawab ka hissa nahi.
     *
     * Poori line se milan hota hai, "contains" se nahi - warna jawab me aaya
     * hua aam shabd bhi poori line uda deta.
     */
    private val DROP_LINES = setOf(
        "search", "sources", "source", "related", "share", "copy", "rewrite",
        "regenerate", "follow up", "follow-up", "ask anything", "ask gemini",
        "ask chatgpt", "answer", "send message", "submit", "stop", "new chat",
        "sign in", "sign up", "log in", "login", "continue", "accept", "close",
        "new tab", "tabs", "bookmark", "bookmarks", "refresh", "reload",
        "home", "back", "forward", "menu", "settings", "more", "open",
        "gemini", "chatgpt", "perplexity", "chrome", "google", "images",
        "videos", "upgrade", "you", "gpt-4", "gpt-5", "temporary chat",
    )

    /** Ye lafz dikhe to matlab login maanga ja raha hai, jawab nahi milega. */
    private val LOGIN_HINTS = listOf(
        "sign in to continue", "log in to continue", "sign in with google",
        "create an account", "you must log in", "verify you are human",
    )

    fun provider(context: Context): Provider {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PROVIDER, null)
            ?: return Provider.GEMINI

        return runCatching { Provider.valueOf(saved) }.getOrDefault(Provider.GEMINI)
    }

    fun setProvider(context: Context, provider: Provider) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROVIDER, provider.name)
            .apply()
    }

    /**
     * Sawaal ka jawab.
     *
     * @return jawab, ya null agar Accessibility off hai / browser khula nahi /
     *   waqt par kuch padhne layak nahi mila
     */
    suspend fun ask(context: Context, prompt: String): String? {
        val question = prompt.trim()
        if (question.length < 3) return null

        // Screen padhe bina ye poora tarika bekaar hai.
        if (!AccessibilityHelper.isEnabled(context)) return null
        if (!EvAccessibilityService.isRunning()) return null

        val first = provider(context)
        val answer = run(context, question, first)
        if (answer != null) return answer

        // Gemini/ChatGPT ne login manga ya page badal gaya - login-free raaste
        // se dobara koshish. User ko isse farq nahi padta, use jawab chahiye.
        if (first != Provider.WEB) {
            return run(context, question, Provider.WEB)
        }

        return null
    }

    // ------------------------------------------------------------- andar ka kaam

    private suspend fun run(context: Context, question: String, provider: Provider): String? {
        if (!open(context, question, provider)) return null

        delay(PAGE_WAIT_MS)

        if (provider != Provider.WEB) {
            if (!submitPrompt(question)) {
                comeBack(context)
                return null
            }
        }

        val screen = awaitStableText()
        comeBack(context)

        if (screen == null) return null
        if (LOGIN_HINTS.any { screen.contains(it, ignoreCase = true) }) return null

        return clean(screen, question)
    }

    private fun open(context: Context, question: String, provider: Provider): Boolean {
        val encoded = URLEncoder.encode(question, "UTF-8")

        val url = when (provider) {
            // Gemini prompt ko URL me nahi leta - wahan likhna padta hai.
            Provider.GEMINI -> "https://gemini.google.com/app"
            // ChatGPT ?q= se aksar khud hi bhej deta hai; na bheje to hum daba denge.
            Provider.CHATGPT -> "https://chatgpt.com/?q=$encoded"
            Provider.WEB -> "https://www.perplexity.ai/search?q=$encoded"
        }

        val uri = Uri.parse(url)

        // Chrome pehle - uska page structure sabse jyada tika hua hai.
        val chrome = Intent(Intent.ACTION_VIEW, uri)
            .setPackage(CHROME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (AppLauncher.startIntent(context, chrome)) return true

        // Chrome nahi hai to jo bhi browser default ho.
        val any = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return AppLauncher.startIntent(context, any)
    }

    /**
     * Box me sawaal likho aur Submit dabao.
     *
     * Ek hi koshish kaafi nahi hoti - page load hone, box aane aur button
     * enable hone me waqt lagta hai. Isliye chhote chhote kadam, baar baar.
     */
    private suspend fun submitPrompt(question: String): Boolean {
        val deadline = SystemClock.elapsedRealtime() + TYPE_TIMEOUT_MS

        while (SystemClock.elapsedRealtime() < deadline) {
            if (EvAccessibilityService.typePromptAndSubmit(question)) return true
            delay(TYPE_STEP_MS)
        }

        return false
    }

    /**
     * Jab tak screen ka text badalta rahe, jawab abhi likha ja raha hai.
     * Text thamte hi wahi hamara jawab hai.
     */
    private suspend fun awaitStableText(): String? {
        val deadline = SystemClock.elapsedRealtime() + ANSWER_TIMEOUT_MS

        var last: String? = null
        var stable = 0

        while (SystemClock.elapsedRealtime() < deadline) {
            val now = EvAccessibilityService.screenText(SCREEN_LIMIT)

            if (now != null && now.length >= MIN_ANSWER_CHARS) {
                if (now == last) {
                    stable++
                    if (stable >= STABLE_ROUNDS) return now
                } else {
                    stable = 0
                }
            }

            last = now
            delay(POLL_MS)
        }

        // Waqt khatam - jitna mila utna hi sahi, agar padhne layak ho.
        return last?.takeIf { it.length >= MIN_ANSWER_CHARS }
    }

    /** Kaam khatam - E.V ko wapas saamne le aao, jawab wahin dikhana hai. */
    private fun comeBack(context: Context) {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return

        AppLauncher.startIntent(context, intent)
    }

    /**
     * Screen ke text me se jawab nikalna.
     *
     * Sawaal khud screen pe likha hota hai, aur jawab uske **neeche** aata hai.
     * Isliye pehle sawaal wali line dhoondte hain aur usse aage ka hissa lete
     * hain - isse browser ka upar wala saara kachra apne aap kat jata hai.
     */
    private fun clean(screen: String, question: String): String? {
        val asked = question.lowercase().trim()
        val all = screen.split("\n").map { it.trim() }

        val askedAt = all.indexOfLast { it.lowercase().contains(asked.take(24)) }
        val after = if (askedAt >= 0 && askedAt < all.size - 1) all.drop(askedAt + 1) else all

        val lines = after.filter { line ->
            val low = line.lowercase()

            line.length >= 4 &&
                low != asked &&
                low !in DROP_LINES &&
                !low.startsWith("http") &&
                !low.startsWith("www.") &&
                !low.endsWith(".com") &&
                // "3 sources", "12 h ago" jaisi choti lines.
                !low.matches(Regex("\\d+\\s*\\w{0,6}"))
        }

        if (lines.isEmpty()) return null

        val answer = lines.joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (answer.length < MIN_ANSWER_CHARS) return null

        return if (answer.length <= MAX_REPLY_CHARS) {
            answer
        } else {
            answer.take(MAX_REPLY_CHARS).substringBeforeLast(' ') + "\u2026"
        }
    }
}
