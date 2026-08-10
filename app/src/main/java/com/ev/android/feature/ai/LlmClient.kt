package com.ev.android.feature.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

sealed interface LlmResult {
    data class Ok(val content: String) : LlmResult
    data class Error(val message: String) : LlmResult
}

/**
 * Groq ka OpenAI-compatible chat endpoint.
 *
 * Groq isliye chuna kyunki assistant me speed hi sab kuch hai \u2014 2 second ruk
 * gaya to command bolne ka fayda hi nahi. Free tier bhi bina credit card ke
 * milta hai.
 *
 * Endpoint OpenAI wala hi hai, to kal ko koi doosra provider chahiye ho to
 * sirf BASE_URL aur MODEL badalna padega.
 */
object LlmClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.1-8b-instant"

    suspend fun complete(
        apiKey: String,
        systemPrompt: String,
        userPrompt: String,
    ): LlmResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext LlmResult.Error("API key nahi hai")

        val payload = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0)
            put("max_tokens", 300)
            put("response_format", JSONObject().put("type", "json_object"))
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", systemPrompt))
                    .put(JSONObject().put("role", "user").put("content", userPrompt)),
            )
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer " + apiKey)
            }

            connection.outputStream.use { it.write(payload.toString().toByteArray()) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream
                    ?.bufferedReader()
                    ?.use(BufferedReader::readText)
                    .orEmpty()
                return@withContext LlmResult.Error(describe(code, error))
            }

            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val content = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()

            if (content.isBlank()) {
                LlmResult.Error("AI ka jawab khali aaya")
            } else {
                LlmResult.Ok(content)
            }
        } catch (e: Exception) {
            LlmResult.Error("AI se baat nahi ho payi \u2014 internet check karo")
        } finally {
            connection?.disconnect()
        }
    }

    private fun describe(code: Int, body: String): String = when (code) {
        401, 403 -> "API key galat lag rahi hai \u2014 settings me check karo"
        429 -> "Free limit khatam ho gayi \u2014 thodi der baad try karo"
        in 500..599 -> "AI server down hai \u2014 baad me try karo"
        else -> {
            val reason = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            if (reason.isNotBlank()) reason else "AI error (" + code + ")"
        }
    }
}
