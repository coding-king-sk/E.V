package com.ev.android.feature.command

/**
 * Hinglish me time nikalne wale chhote helpers.
 *
 * Log "5 minute" bhi bolte hain aur "paanch minute" bhi, isliye digits aur
 * Hindi ginti dono handle karte hain.
 */
internal object TimeParser {

    private val numberWords = mapOf(
        "ek" to 1, "do" to 2, "teen" to 3, "char" to 4, "chaar" to 4,
        "paanch" to 5, "panch" to 5, "cheh" to 6, "chhe" to 6, "saat" to 7,
        "aath" to 8, "nau" to 9, "das" to 10, "gyarah" to 11, "barah" to 12,
        "pandrah" to 15, "bees" to 20, "pachees" to 25, "tees" to 30,
        "chalis" to 40, "paintalis" to 45, "pachas" to 50,
    )

    private val secondUnits = listOf("second", "seconds", "sec", "secs", "sekand")
    private val minuteUnits = listOf("minute", "minutes", "min", "mins", "minit")
    private val hourUnits = listOf("hour", "hours", "ghanta", "ghante", "ghanto")

    /** "5 minute", "paanch minute", "30 second" -> seconds. */
    fun durationSeconds(text: String): Int? {
        val tokens = text.split(" ").filter { it.isNotBlank() }

        for (index in tokens.indices) {
            val unit = tokens[index]
            val multiplier = when {
                secondUnits.contains(unit) -> 1
                minuteUnits.contains(unit) -> 60
                hourUnits.contains(unit) -> 3600
                else -> continue
            }

            // Unit se pehle wala token hi number hota hai: "5 minute".
            val amount = tokens.getOrNull(index - 1)?.let { valueOf(it) }
                ?: tokens.firstNotNullOfOrNull { valueOf(it) }
                ?: continue

            if (amount > 0) return amount * multiplier
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
                ?: text.split(" ").firstNotNullOfOrNull { numberWords[it] }
                ?: return null
            minute = 0
        }

        if (hour !in 0..23 || minute !in 0..59) return null

        val morning = text.contains("subah") || text.contains("am") || text.contains("morning")
        val evening = text.contains("raat") || text.contains("shaam") || text.contains("sham") ||
            text.contains("pm") || text.contains("dopahar") || text.contains("evening") ||
            text.contains("night")

        if (evening && hour in 1..11) hour += 12
        if (morning && hour == 12) hour = 0

        return hour to minute
    }

    private fun valueOf(token: String): Int? =
        token.toIntOrNull() ?: numberWords[token]
}
