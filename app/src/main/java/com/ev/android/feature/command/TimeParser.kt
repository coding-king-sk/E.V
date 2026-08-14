package com.ev.android.feature.command

import java.util.Calendar
import java.util.TimeZone

/**
 * Hinglish me time nikalne wale chhote helpers.
 *
 * Log "5 minute" bhi bolte hain aur "paanch minute" bhi, isliye digits aur
 * Hindi ginti dono handle karte hain. Spelling ki bhi koi ginti nahi —
 * Google ka recognizer "minute" ko "minut" ya "mint" bhi likh deta hai.
 */
internal object TimeParser {

    /** Timer ki upper limit — 24 ghante se zyada ka timer galti hi hoti hai. */
    private const val MAX_DURATION_SECONDS = 24 * 3600

    private val numberWords = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4,
        "paanch" to 5, "panch" to 5, "cheh" to 6, "chhe" to 6, "saat" to 7,
        "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "pandrah" to 15, "bees" to 20, "pachees" to 25, "tees" to 30,
        "chalis" to 40, "paintalis" to 45, "pachas" to 50,
    )

    private val secondUnits = listOf(
        "second", "seconds", "sec", "secs", "sekand", "sekend", "secound",
    )
    private val minuteUnits = listOf(
        "minute", "minutes", "min", "mins", "minit", "minut", "minuts", "mint", "minat",
    )
    private val hourUnits = listOf(
        "hour", "hours", "hr", "hrs", "ghanta", "ghante", "ghanto",
    )

    /**
     * Shabdon ke beech ka koi bhi nishan (space, comma, full stop) todne wala
     * maana jata hai.
     *
     * Pehle sirf space par tokens toote the, isliye "5 minute, yaad dilana" me
     * unit "minute," ban jati thi aur kisi list se match hi nahi hoti thi.
     */
    private val tokenSplitter = Regex("[^\\p{L}\\p{N}]+")

    /** "5 minute", "paanch minute", "30 second" -> seconds. */
    fun durationSeconds(text: String): Int? {
        val tokens = text.split(tokenSplitter).filter { it.isNotBlank() }

        for (index in tokens.indices) {
            val unit = tokens[index]
            val multiplier = when {
                secondUnits.contains(unit) -> 1
                minuteUnits.contains(unit) -> 60
                hourUnits.contains(unit) -> 3600
                else -> continue
            }

            // Unit se pehle wala token hi number hota hai: "5 minute".
            //
            // Pehle yahan "kahin se bhi koi number utha lo" wala fallback tha,
            // jisse "7 baje wala 5 minute timer" me galat number chun jata tha.
            // Number na mile to is unit ko chhod ke aage dekh lete hain.
            val amount = tokens.getOrNull(index - 1)?.let { valueOf(it) } ?: continue

            if (amount > 0) {
                return (amount.toLong() * multiplier)
                    .coerceAtMost(MAX_DURATION_SECONDS.toLong())
                    .toInt()
            }
        }

        return null
    }

    /** "subah 7 baje", "raat 9:30", "7 30" -> hour/minute (24h). */
    fun clockTime(text: String): Pair<Int, Int>? {
        val explicit = Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{2})").find(text)
        var hour: Int
        var minute: Int

        if (explicit != null) {
            hour = explicit.groupValues[1].toIntOrNull() ?: return null
            minute = explicit.groupValues[2].toIntOrNull() ?: 0
        } else {
            val single = Regex("(^|\\s)(\\d{1,2})(\\s|$)").find(text)
            hour = single?.groupValues?.get(2)?.toIntOrNull()
                ?: text.split(tokenSplitter).firstNotNullOfOrNull { numberWords[it] }
                ?: return null
            minute = 0
        }

        if (hour !in 0..23 || minute !in 0..59) return null

        // Yahan poore shabd hi dekhne hain. Pehle seedha contains() tha, aur
        // "am" to Hinglish me har doosre shabd me chhupa hota hai — "kaam",
        // "naam", "shaam", "salaam". Isi wajah se "12 baje kaam yaad dilana"
        // subah maana jata tha aur reminder aadhi raat 00:00 pe set hota tha.
        val morning = containsWord(text, "subah") || containsWord(text, "am") ||
            containsWord(text, "morning")
        val night = containsWord(text, "raat") || containsWord(text, "night")
        val evening = night || containsWord(text, "shaam") || containsWord(text, "sham") ||
            containsWord(text, "pm") || containsWord(text, "dopahar") ||
            containsWord(text, "evening")

        if (evening && hour in 1..11) hour += 12
        if (morning && hour == 12) hour = 0
        // "raat 12 baje" ka matlab aadhi raat hai, dopahar ke 12 nahi.
        if (night && hour == 12) hour = 0

        return hour to minute
    }

    /**
     * Reminder ka exact waqt (epoch millis).
     *
     * Do tarah se bolte hain log:
     *  - "10 minute baad yaad dilana"        -> abhi se itni der baad
     *  - "kal subah 8 baje yaad dilana"      -> agle din 08:00
     *
     * Agar sirf time bola ho ("shaam 7 baje") aur wo waqt aaj nikal chuka ho,
     * to agle din maan lete hain — warna reminder turant bajta, jo bekaar hai.
     *
     * @param now testing ke liye inject kiya ja sakta hai
     */
    fun reminderMillis(
        text: String,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
    ): Long? {
        relativeDelaySeconds(text)?.let { return now + it * 1000L }

        val time = clockTime(text) ?: return null

        val calendar = Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, time.first)
            set(Calendar.MINUTE, time.second)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayOffset = when {
            containsWord(text, "parso") -> 2
            containsWord(text, "kal") -> 1
            else -> 0
        }
        if (dayOffset > 0) calendar.add(Calendar.DAY_OF_YEAR, dayOffset)

        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)

        return calendar.timeInMillis
    }

    /** "10 minute baad", "do ghante baad" -> seconds. */
    private fun relativeDelaySeconds(text: String): Int? {
        val later = containsWord(text, "baad") || containsWord(text, "bad") ||
            containsWord(text, "later")
        if (!later) return null
        return durationSeconds(text)
    }

    /**
     * Poora shabd match — aage-peeche koi doosra akshar nahi hona chahiye.
     *
     * Boundary sirf akshar dekhti hai, space nahi, isliye "subah-subah" aur
     * "7am" jaise dono roop pakde jaate hain.
     */
    private fun containsWord(text: String, word: String): Boolean =
        Regex(
            "(?<![\\p{L}\\p{M}])" + Regex.escape(word) + "(?![\\p{L}\\p{M}])",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(text)

    private fun valueOf(token: String): Int? =
        token.toIntOrNull() ?: numberWords[token]
}
