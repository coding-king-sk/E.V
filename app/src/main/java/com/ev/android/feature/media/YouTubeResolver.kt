package com.ev.android.feature.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Finds the video id of the FIRST search result on YouTube.
 *
 * Why this exists: MEDIA_PLAY_FROM_SEARCH only opens YouTube's search screen on
 * most builds — it does not actually start the top video. Once we know the
 * concrete video id we can open a /watch?v=<id> deep link, which the YouTube app
 * always autoplays.
 *
 * No API key needed — we read the public results page and pull the first
 * "videoId" out of the embedded JSON.
 */
object YouTubeResolver {

    private val VIDEO_ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    /** `sp=EgIQAQ%3D%3D` = filter results to "Video" only, so we never land on a channel or playlist. */
    private const val VIDEO_ONLY_FILTER = "&sp=EgIQAQ%3D%3D"

    /** How much of the tail we keep when trimming, so a match can never be cut in half. */
    private const val OVERLAP_CHARS = 32_000

    private const val MAX_WINDOW_CHARS = 200_000

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    suspend fun firstVideoId(query: String): String? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://www.youtube.com/results?search_query=$encoded$VIDEO_ONLY_FILTER")

            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }

            if (connection.responseCode !in 200..299) return@withContext null

            // Stream it: the page is ~1 MB but the first videoId shows up early,
            // so we bail out as soon as we find one instead of buffering it all.
            connection.inputStream.bufferedReader().use { reader ->
                val window = StringBuilder()
                val chunk = CharArray(16 * 1024)
                while (true) {
                    val count = reader.read(chunk)
                    if (count <= 0) break
                    window.append(chunk, 0, count)

                    VIDEO_ID.find(window)?.let { match ->
                        return@withContext match.groupValues[1]
                    }

                    // Keep a generous tail only, so memory stays flat on long
                    // pages while a "videoId" sitting exactly on a chunk border
                    // still survives the trim.
                    if (window.length > MAX_WINDOW_CHARS) {
                        window.delete(0, window.length - OVERLAP_CHARS)
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }
}
