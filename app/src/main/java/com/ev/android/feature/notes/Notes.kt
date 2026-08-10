package com.ev.android.feature.notes

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.json.JSONArray
import org.json.JSONObject

/** Ek note — bas text aur kab likha. */
data class Note(val text: String, val at: Long)

/**
 * Chhote notes, sirf is phone me.
 *
 * Jaan bujh ke koi account, sync ya cloud nahi. Notes me log aksar OTP, address
 * aur paise ka hisaab likhte hain — wo cheezein kahin bahar bhejna theek nahi
 * lagta. Isliye SharedPreferences, jo app hatane pe saath hi chala jata hai.
 */
object Notes {

    private const val PREFS = "ev_notes"
    private const val KEY = "items"
    private const val MAX = 200

    val items: SnapshotStateList<Note> = mutableStateListOf()

    fun load(context: Context) {
        if (items.isNotEmpty()) return

        val raw = prefs(context).getString(KEY, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val loaded = (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Note(text = o.optString("text"), at = o.optLong("at"))
            }
            items.clear()
            items.addAll(loaded)
        }
    }

    fun add(context: Context, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        items.add(0, Note(text = clean, at = System.currentTimeMillis()))
        while (items.size > MAX) items.removeAt(items.lastIndex)
        save(context)
    }

    fun remove(context: Context, note: Note) {
        items.remove(note)
        save(context)
    }

    fun clear(context: Context) {
        items.clear()
        save(context)
    }

    private fun save(context: Context) {
        val array = JSONArray()
        items.forEach { note ->
            array.put(
                JSONObject()
                    .put("text", note.text)
                    .put("at", note.at)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
