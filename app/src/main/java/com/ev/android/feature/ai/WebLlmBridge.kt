package com.ev.android.feature.ai

import android.accessibilityservice.AccessibilityService
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
 * Bina API key ke jawab lane wala raasta.
 *
 * Idea seedha hai: jo sawaal E.V khud nahi samajh paya, wo browser me khol do,
 * jawab screen pe aane do, aur usi screen ko Accessibility se padh kar user ko
 * suna do. Na koi key, na koi bill, na user ko kuch setup karna.
 *
 * **Do baatein saaf reh jani chahiye:**
 *
 *  1. Ye sach me "background" nahi hai. Android kisi bhi app ko chhupi hui
 *     window ka text nahi padhne deta — Accessibility sirf wahi dekh sakti hai
 *     jo screen pe actually dikh raha ho. Isliye browser ek pal ke liye saamne
 *     aayega aur jawab milte hi E.V wapas aa jayega. Ise chhupaya nahi ja
 *     sakta; jo bhi "background browser" ka dawa karta hai wo ya to root maangta
 *     hai ya jhooth bolta hai.
 *  2. Isliye jaan-boojh ke aisi jagah chuni hai jahan **login nahi lagta** aur
 *     sawaal seedha URL me chala jata hai. Gemini/ChatGPT me login chahiye hota
 *     hai aur unka page har mahine badalta rehta hai — wahan typing aur submit
 *     wala tarika roz tootta.
 *
 * Kaam ka tarika:
 *  sawaal -> URL -> browser khula -> screen ka text baar baar padho -> jab text
 *  badalna band ho jaye (matlab jawab poora aa gaya) -> saaf karke wapas.
 */
object WebLlmBridge {

    /** Page khulne ka pehla intezaar. */
    private const val FIRST_WAIT_MS = 3_500L

    /** Do baar padhne ke beech ka gap. */
    private const val POLL_MS = 1_200L

    /** Itni der me jawab na aaye to haar maan lo. */
    private const val MAX_WAIT_MS = 40_000L

    /** Itni baar text na badle to maan lete hain ki jawab poora ho gaya. */
    private const val STABLE_ROUNDS = 3

    /** Itne se chhote text ko jawab maanna galat hai (wo page loading hoga). */
    private const val MIN_ANSWER_CHARS = 60

    /** Sunane layak lambai — isse zyada bolna kisi ko pasand nahi aata. */
    private const val MAX_REPLY_CHARS = 700

    private const val SCREEN_LIMIT = 6_000

    /**
     * Bina login wali jagah — sawaal seedha URL me chala jata hai aur jawab
     * apne aap likha jaata hai. Isliye na typing chahiye, na submit dabana.
     */
    private const val SEARCH_URL = "https://www.perplexity.ai/search?q="

    /**
     * Browser ki apni cheezein — ye jawab ka hissa nahi hain.
     *
     * Poori line se milan karte hain, "contains" se nahi — warna jawab me aaya
     * hua aam shabd bhi line ko uda deta.
     */
    private val DROP_LINES = setOf(
        "search", "sources", "source", "related", "share", "copy", "rewrite",
        "follow up", "follow-up", "ask anything", "ask follow-up", "answer",
        "sign in", "sign up", "log in", "login", "continue", "accept", "close",
        "new tab", "tabs", "bookmark", "bookmarks", "refresh", "reload",
        "home", "back", "forward", "menu", "settings", "more", "open",
        "perplexity", "images", "videos", "steps", "pro", "library", "discover",
    )

    /**
     * Sawaal ka jawab browser se.
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

        if (!openInBrowser(context, question)) return null

        delay(FIRST_WAIT_MS)

        val screen = awaitStableText() ?: run {
            comeBack()
            return null
        }

        comeBack()

        return clean(screen, question)
    }

    private fun openInBrowser(context: Context, question: String): Boolean {
        val url = SEARCH_URL + URLEncoder.encode(question, "UTF-8")

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return AppLauncher.startIntent(context, intent)
    }

    /**
     * Jab tak screen ka text badalta rahe, jawab abhi likha ja raha hai.
     * Text thamte hi wahi hamara jawab hai.
     */
    private suspend fun awaitStableText(): String? {
        val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MS

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

        // Waqt khatam — jitna mila utna hi sahi, agar padhne layak ho.
        return last?.takeIf { it.length >= MIN_ANSWER_CHARS }
    }

    /** Kaam khatam — browser ko peeche karke user ko wapas laao. */
    private fun comeBack() {
        EvAccessibilityService.performGlobal(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /**
     * Screen ke text me se jawab nikalna.
     *
     * Browser ke buttons, tab ka naam, URL aur khud sawaal — sab hata dete hain.
     * Jo bacha wahi jawab hai.
     */
    private fun clean(screen: String, question: String): String? {
        val asked = question.lowercase().trim()

        val lines = screen.split("\n")
            .map { it.trim() }
            .filter { line ->
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
