package com.ev.android.feature.weather

import android.content.Context
import com.ev.android.feature.location.EvLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * "Aaj mausam kaisa hai", "kal baarish hogi kya".
 *
 * Data Open-Meteo se aata hai. Ye jaan-boojh ke chuna gaya hai: bilkul free
 * hai, koi API key nahi maangta aur koi account nahi banana padta - matlab
 * user ko kuch setup kiye bina hi ye feature chal jata hai.
 *
 * Location wahi last-known wali hai jo EvLocation deta hai. Mausam ke liye
 * itni sateek jagah kaafi hai; do-teen kilometre ka farak mausam nahi badalta.
 */
object EvWeather {

    private const val ENDPOINT = "https://api.open-meteo.com/v1/forecast"
    private const val TIMEOUT_MS = 12000

    /** @param dayOffset 0 = aaj, 1 = kal, 2 = parso */
    suspend fun describe(context: Context, dayOffset: Int): String = withContext(Dispatchers.IO) {
        val point = EvLocation.coordinates(context)
            ?: return@withContext "Mausam ke liye location chahiye \u2014 phone ki location " +
                "on karo aur E.V ko location ki permission de do"

        val json = fetch(point.first, point.second)
            ?: return@withContext "Mausam nahi mil paya \u2014 internet check karo"

        val place = EvLocation.shortPlace(context)

        runCatching {
            if (dayOffset <= 0) todayLine(json, place) else laterLine(json, dayOffset)
        }.getOrElse { "Mausam ka jawab samajh nahi aaya" }
    }

    /**
     * Abhi ka haal.
     *
     * "Mehsoos kitna hota hai" tabhi bolte hain jab asli temperature se do
     * degree se zyada farak ho \u2014 warna wahi baat do baar kehne jaisa lagta hai.
     */
    private fun todayLine(json: JSONObject, place: String?): String {
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")

        val temp = current.getDouble("temperature_2m").roundToInt()
        val feels = current.getDouble("apparent_temperature").roundToInt()
        val sky = skyOf(current.getInt("weather_code"))

        val max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt()
        val min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt()
        val rain = daily.getJSONArray("precipitation_probability_max").optDouble(0, -1.0)

        val head = if (place != null) {
            place + " me abhi " + temp + "\u00B0"
        } else {
            "Abhi " + temp + "\u00B0"
        }

        val parts = mutableListOf(head + " hai, " + sky)
        if (abs(feels - temp) >= 2) parts.add("mehsoos " + feels + "\u00B0 hota hai")
        parts.add("aaj " + min + "\u00B0 se " + max + "\u00B0 tak rahega")
        if (rain >= 0) parts.add("baarish ka chance " + rain.roundToInt() + "%")

        return parts.joinToString(", ") + "."
    }

    private fun laterLine(json: JSONObject, dayOffset: Int): String {
        val daily = json.getJSONObject("daily")
        val index = dayOffset.coerceIn(1, 3)

        val codes = daily.getJSONArray("weather_code")
        if (index >= codes.length()) return "Itni door ka mausam nahi mila"

        val sky = skyOf(codes.getInt(index))
        val max = daily.getJSONArray("temperature_2m_max").getDouble(index).roundToInt()
        val min = daily.getJSONArray("temperature_2m_min").getDouble(index).roundToInt()
        val rain = daily.getJSONArray("precipitation_probability_max").optDouble(index, -1.0)

        val day = if (index == 1) "Kal" else "Parso"
        val tail = if (rain >= 0) ", baarish ka chance " + rain.roundToInt() + "%" else ""

        return day + " " + sky + ", " + min + "\u00B0 se " + max + "\u00B0" + tail + "."
    }

    /** WMO ke code \u2014 Open-Meteo inhi numbers me mausam batata hai. */
    private fun skyOf(code: Int): String = when (code) {
        0 -> "aasman bilkul saaf"
        1 -> "zyadatar saaf"
        2 -> "thode baadal"
        3 -> "baadal chhaye hue"
        45, 48 -> "kohra"
        51, 53, 55 -> "halki phuhaar"
        56, 57 -> "thandi phuhaar"
        61 -> "halki baarish"
        63 -> "baarish"
        65 -> "tez baarish"
        66, 67 -> "thandi baarish"
        71, 73, 75, 77 -> "barfbari"
        80 -> "halki bauchhar"
        81 -> "bauchhar"
        82 -> "tez bauchhar"
        85, 86 -> "barf ki bauchhar"
        95 -> "aandhi-toofan"
        96, 99 -> "aandhi ke saath ole"
        else -> "mausam badalta hua"
    }

    /**
     * Network call.
     *
     * Plain HttpURLConnection \u2014 poori app me yahi chalta hai, sirf mausam ke
     * liye koi nayi library jodna theek nahi.
     */
    private fun fetch(lat: Double, lon: Double): JSONObject? = runCatching {
        val url = ENDPOINT +
            "?latitude=" + coord(lat) +
            "&longitude=" + coord(lon) +
            "&current=temperature_2m,apparent_temperature,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min," +
            "precipitation_probability_max" +
            "&timezone=auto&forecast_days=4"

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

        try {
            if (connection.responseCode !in 200..299) {
                null
            } else {
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private fun coord(value: Double): String = String.format(Locale.US, "%.4f", value)
}
