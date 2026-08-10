package com.ev.android.feature.ai

import android.content.Context
import com.ev.android.feature.settings.EvSettings

/**
 * Jab baat command hai hi nahi \u2014 "Bhopal me kal mausam kaisa rahega",
 * "biryani kaise banti hai" \u2014 tab seedha Groq se jawab le kar bol dete hain.
 *
 * Yahi wo pipeline hai: aawaz -> text -> Groq -> text -> aawaz.
 *
 * Jawab jaan bujh ke chhota rakha gaya hai, kyunki ye padha nahi jaata,
 * **sunaya** jaata hai. Lambe paragraph sun ke koi nahi rukta.
 */
object Conversation {

    private const val SYSTEM = "Tum E.V ho, ek Android voice assistant. " +
        "Jawab Hinglish me do (Roman script me, Devanagari me nahi). " +
        "Bahut chhota jawab do \u2014 zyada se zyada 2 se 3 line. " +
        "Tumhara jawab bol kar sunaya jayega, isliye markdown, bullet points, " +
        "emoji, headings, ya code bilkul mat likhna. Sirf saada bolne wala text. " +
        "Agar kisi baat ka pakka pata na ho to seedha keh do ki pata nahi hai."

    /** @return bolne layak jawab, ya null agar AI on hi nahi hai. */
    suspend fun answer(context: Context, question: String): String? {
        if (question.isBlank()) return null
        if (!EvSettings.aiEnabled(context)) return null

        val result = LlmClient.complete(
            apiKey = EvSettings.apiKey(context),
            systemPrompt = SYSTEM,
            userPrompt = question,
            json = false,
            temperature = 0.5,
            maxTokens = 200,
        )

        return when (result) {
            is LlmResult.Ok -> speakable(result.content)
            is LlmResult.Error -> result.message
        }
    }

    /** Model kabhi kabhi phir bhi markdown thok deta hai \u2014 usse saaf karte hain. */
    private fun speakable(raw: String): String {
        val cleaned = raw
            .replace(Regex("[*_`#>]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return if (cleaned.length > 600) cleaned.take(600) else cleaned
    }
}
