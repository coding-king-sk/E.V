package com.ev.android.feature.history

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ev.android.feature.command.EvCommand
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HistoryEntry(
    val spoken: String,
    val understood: String,
    val reply: String,
    val at: Long,
)

/**
 * Command history.
 *
 * Ye sirf "acchha lagta hai" wala feature nahi hai — iska asli faida debugging
 * hai. Jab koi command galat chale, history se turant pata chal jata hai ki
 * galti kahan hui: recognizer ne galat suna, ya parser ne galat samjha.
 *
 * Sab kuch phone ke andar SharedPreferences me rehta hai; kahin bheja nahi jata.
 */
object CommandHistory {

    private const val PREFS = "ev_history"
    private const val KEY = "entries"

    /** Itne se zyada nahi rakhte — prefs me bada JSON theek nahi. */
    private const val MAX = 50

    /** Compose ise seedhe observe karta hai. */
    val entries: SnapshotStateList<HistoryEntry> = mutableStateListOf()

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true

        val raw = prefs(context).getString(KEY, null) ?: return

        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                entries.add(
                    HistoryEntry(
                        spoken = item.optString("spoken"),
                        understood = item.optString("understood"),
                        reply = item.optString("reply"),
                        at = item.optLong("at"),
                    )
                )
            }
        }
    }

    @Synchronized
    fun add(context: Context, spoken: String, understood: String, reply: String) {
        load(context)

        entries.add(
            0,
            HistoryEntry(
                spoken = spoken.trim(),
                understood = understood,
                reply = reply,
                at = System.currentTimeMillis(),
            ),
        )

        while (entries.size > MAX) entries.removeAt(entries.lastIndex)
        save(context)
    }

    @Synchronized
    fun clear(context: Context) {
        entries.clear()
        save(context)
    }

    /**
     * Command ka aasan sa description — history me dikhane ke liye.
     *
     * Dhyan rahe: ye 'when' EvCommand par exhaustive hai. Jab bhi EvCommand me
     * naya member add ho, usi push me yahan bhi branch add karni hogi, warna
     * compile fail hoti hai.
     */
    fun describe(command: EvCommand): String = when (command) {
        is EvCommand.OpenApp -> "App kholo: " + command.target.label
        is EvCommand.PlayMedia -> "Chalao \"" + command.query + "\" (" + command.target.label + ")"
        is EvCommand.SearchInApp -> "Search \"" + command.query + "\" (" + command.target.label + ")"
        is EvCommand.WebSearch -> "Google: " + command.query
        is EvCommand.SendWhatsApp -> "WhatsApp \u2192 " + (command.contactName ?: "?")
        is EvCommand.SendSms -> "SMS \u2192 " + command.contactName
        is EvCommand.CallContact -> "Call \u2192 " + command.contactName
        is EvCommand.WhatsAppCall ->
            (if (command.video) "WhatsApp video call \u2192 " else "WhatsApp call \u2192 ") +
                command.contactName
        is EvCommand.Media -> "Media: " + command.action.name
        EvCommand.IdentifySong -> "Gaana pehchano"
        is EvCommand.Timer -> "Timer: " + command.seconds + " second"
        is EvCommand.Alarm -> "Alarm: " + command.hour + ":" + command.minute.toString().padStart(2, '0')
        is EvCommand.Reminder ->
            "Reminder " + SimpleDateFormat("d MMM h:mm a", Locale.getDefault())
                .format(Date(command.at)) + ": " + command.text
        is EvCommand.Note -> "Note: " + command.text
        is EvCommand.Info -> "Sawaal: " + command.kind.name
        EvCommand.WhereAmI -> "Meri location"
        is EvCommand.Weather -> when (command.dayOffset) {
            0 -> "Mausam: aaj"
            1 -> "Mausam: kal"
            else -> "Mausam: parso"
        }
        is EvCommand.Calculate -> "Hisaab: " + command.expression
        is EvCommand.TakePhoto -> if (command.front) "Selfie" else "Photo"
        is EvCommand.RecordVideo -> "Video: " + command.seconds + " second"
        is EvCommand.TypeText ->
            "Type \"" + command.text + "\"" +
                (command.target?.let { " (" + it.label + ")" } ?: "")
        is EvCommand.Multi ->
            command.commands.joinToString(" + ") { describe(it) }
        is EvCommand.Device -> "Device: " + command.action.name
        is EvCommand.Unknown -> "Samajh nahi aaya"
    }

    private fun save(context: Context) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("spoken", entry.spoken)
                    .put("understood", entry.understood)
                    .put("reply", entry.reply)
                    .put("at", entry.at)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
