package com.ev.android.feature.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Groq ka Whisper — awaaz se text.
 *
 * Google ka recognizer Hinglish pe aksar dhokha de deta hai ("rehan ko call
 * lagao" ko "rain ko col lagao" bana deta hai). Whisper mixed language kaafi
 * behtar pakadta hai.
 *
 * Iski keemat: awaaz Groq ke server pe jaati hai aur internet chahiye. Isiliye
 * ye default off hai — user khud Settings me on kare tabhi chalta hai.
 */
object GroqAudio {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
    private const val MODEL = "whisper-large-v3-turbo"
    private const val BOUNDARY = "----EvBoundary7f83a2c1"

    /**
     * @param language ISO code. "hi" isliye kyunki Hinglish me Hindi hi zyada
     *   hoti hai; Whisper English words phir bhi theek likh deta hai.
     */
    suspend fun transcribe(
        apiKey: String,
        file: File,
        language: String = "hi",
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Groq key nahi hai"))
        }
        if (!file.exists() || file.length() < 1024L) {
            return@withContext Result.failure(IllegalStateException("Kuch record hi nahi hua"))
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty(
                    "Content-Type",
                    "multipart/form-data; boundary=" + BOUNDARY,
                )
            }

            DataOutputStream(connection.outputStream.buffered()).use { out ->
                field(out, "model", MODEL)
                field(out, "language", language)
                field(out, "response_format", "json")

                out.writeBytes("--" + BOUNDARY + "\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; " +
                        "filename=\"audio.wav\"\r\n"
                )
                out.writeBytes("Content-Type: audio/wav\r\n\r\n")
                file.inputStream().use { it.copyTo(out) }
                out.writeBytes("\r\n--" + BOUNDARY + "--\r\n")
                out.flush()
            }

            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream
                    ?.bufferedReader()
                    ?.use(BufferedReader::readText)
                    .orEmpty()
                return@withContext Result.failure(IllegalStateException(describe(code, error)))
            }

            val body = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            val text = JSONObject(body).optString("text").trim()

            if (text.isEmpty()) {
                Result.failure(IllegalStateException("Kuch sunai nahi diya"))
            } else {
                Result.success(text)
            }
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Whisper se baat nahi ho payi \u2014 internet check karo"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun field(out: DataOutputStream, name: String, value: String) {
        out.writeBytes("--" + BOUNDARY + "\r\n")
        out.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
        out.writeBytes(value + "\r\n")
    }

    private fun describe(code: Int, body: String): String = when (code) {
        401, 403 -> "API key galat lag rahi hai \u2014 settings me check karo"
        413 -> "Recording bahut lambi ho gayi"
        429 -> "Free limit khatam \u2014 thodi der baad try karo"
        in 500..599 -> "Groq server down hai"
        else -> {
            val reason = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            if (reason.isNotBlank()) reason else "Whisper error (" + code + ")"
        }
    }
}
