package com.ev.android.feature.info

import java.util.Locale

/**
 * Chhota sa hisaab \u2014 "1500 ka 18% kitna hota hai", "250 plus 300".
 *
 * Yahan poora expression parser jaan-boojh ke nahi banaya. Bolne me log ek hi
 * baar me do number aur ek kaam bolte hain; usse zyada complex sawaal AI ke
 * paas chala jayega. Kam code, kam galti.
 */
object Calculator {

    private const val PERCENT = "%|percent|persent|parsent|pratishat|fisadi"
    private const val NUM = "(\\d+(?:\\.\\d+)?)"

    /** "1500 ka 18%" \u2014 sabse aam shakl. */
    private val percentOf = Regex("$NUM\\s*(?:ka|ki|ke|of)\\s*$NUM\\s*(?:$PERCENT)")

    /** "18% of 1500" \u2014 ulti shakl. */
    private val percentOfReversed = Regex("$NUM\\s*(?:$PERCENT)\\s*(?:ka|of|me se|mein se)\\s*$NUM")

    private val plus = Regex("$NUM\\s*(?:\\+|plus|jama|aur jod)\\s*$NUM")
    private val minus = Regex("$NUM\\s*(?:-|minus|ghata|kam)\\s*$NUM")
    private val times = Regex("$NUM\\s*(?:x|\\*|into|guna|multiply by|times)\\s*$NUM")
    private val divide = Regex("$NUM\\s*(?:/|bhag|divided by|divide by|batta)\\s*$NUM")

    /**
     * Jawab, ya null agar sawaal hisaab ka hai hi nahi.
     *
     * Jawab me sawaal bhi dohraya jata hai ("1500 ka 18% = 270") taaki bolne
     * par saaf lage ki kis cheez ka jawab hai.
     */
    fun evaluate(text: String): String? {
        percentOf.find(text)?.let { m ->
            val whole = m.groupValues[1].toDoubleOrNull() ?: return null
            val percent = m.groupValues[2].toDoubleOrNull() ?: return null
            return num(whole) + " ka " + num(percent) + "% = " + num(whole * percent / 100.0)
        }

        percentOfReversed.find(text)?.let { m ->
            val percent = m.groupValues[1].toDoubleOrNull() ?: return null
            val whole = m.groupValues[2].toDoubleOrNull() ?: return null
            return num(whole) + " ka " + num(percent) + "% = " + num(whole * percent / 100.0)
        }

        binary(plus, text) { a, b -> a + b }?.let { return it }
        binary(times, text) { a, b -> a * b }?.let { return it }
        binary(divide, text) { a, b -> if (b == 0.0) Double.NaN else a / b }?.let { return it }
        binary(minus, text) { a, b -> a - b }?.let { return it }

        return null
    }

    private fun binary(regex: Regex, text: String, op: (Double, Double) -> Double): String? {
        val m = regex.find(text) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val b = m.groupValues[2].toDoubleOrNull() ?: return null

        val result = op(a, b)
        if (result.isNaN() || result.isInfinite()) return "Zero se bhag nahi hota"

        return num(a) + " " + m.groupValues[0]
            .replace(m.groupValues[1], "")
            .replace(m.groupValues[2], "")
            .trim() + " " + num(b) + " = " + num(result)
    }

    /** 270.0 ko "270" likhta hai, 33.33 ko "33.33". */
    private fun num(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.ENGLISH, "%.2f", value)
        }
}
