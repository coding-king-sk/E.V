package com.ev.android.feature.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.ev.android.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Reminder(val id: Int, val at: Long, val text: String)

/**
 * E.V ke apne reminders.
 *
 * Alarm aur timer to phone ki Clock app ko de dete hain, lekin wahan sirf awaz
 * bajti hai — "kya karna tha" nahi pata chalta. Reminder E.V khud rakhta hai,
 * aur waqt aane par bol ke batata hai.
 *
 * Reboot ke baad Android saare alarms bhool jata hai, isliye list prefs me
 * save rehti hai aur BOOT_COMPLETED pe dobara set ho jati hai.
 */
object Reminders {

    const val CHANNEL_ID = "ev_reminders"
    const val EXTRA_ID = "reminder_id"
    const val EXTRA_TEXT = "reminder_text"
    const val ACTION_FIRE = "com.ev.android.action.REMINDER"

    private const val PREFS = "ev_reminders"
    private const val KEY = "items"

    /**
     * @return false tabhi jab system ne alarm set hi na karne diya
     */
    fun schedule(context: Context, at: Long, text: String): Boolean {
        // Channel pehle. Pehle ye alarm ke BAAD banta tha — aur agar beech me
        // process mar jata to reminder bajne ke waqt channel hota hi nahi,
        // jiska matlab hai notification chup-chaap gayab.
        ensureChannel(context)

        val pending = all(context).filter { it.at > System.currentTimeMillis() }
        val reminder = Reminder(id = nextId(pending), at = at, text = text)

        if (!setAlarm(context, reminder)) return false

        save(context, pending + reminder)
        return true
    }

    fun all(context: Context): List<Reminder> {
        val raw = prefs(context).getString(KEY, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                Reminder(
                    id = item.optInt("id"),
                    at = item.optLong("at"),
                    text = item.optString("text"),
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Sirf wo reminders jo abhi bajne baaki hain — Settings me yahi dikhte hain. */
    fun upcoming(context: Context): List<Reminder> {
        val now = System.currentTimeMillis()
        return all(context).filter { it.at > now }.sortedBy { it.at }
    }

    /**
     * Kya system hume theek waqt pe bajane dega.
     *
     * Ab ye sirf ek ishara hai, majboori nahi — [setAlarm] pehle
     * `setAlarmClock` try karta hai, jise koi permission nahi chahiye.
     */
    fun canBeExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false
        return runCatching { manager.canScheduleExactAlarms() }.getOrDefault(false)
    }

    /** Notification band hai to reminder dikhega nahi — sirf awaaz aayegi. */
    fun notificationsBlocked(context: Context): Boolean =
        runCatching {
            !NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)

    fun remove(context: Context, id: Int) {
        save(context, all(context).filter { it.id != id })
    }

    /**
     * Reminder hatana.
     *
     * `FLAG_NO_CREATE` isliye: pehle yahan `FLAG_UPDATE_CURRENT` tha, jo
     * PendingIntent **bana** deta hai agar wo pehle se na ho — yani cancel
     * karte waqt ek nayi khaali PendingIntent banti thi (khaali text ke
     * saath). Ab hum wahi purani wali dhoondte hain; na mile to cancel karne
     * ke liye kuch hai hi nahi, sirf list se hata dete hain.
     */
    fun cancel(context: Context, id: Int) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending: PendingIntent? = PendingIntent.getBroadcast(
            context,
            id,
            fireIntent(context, id, ""),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )

        if (pending != null) {
            runCatching { manager?.cancel(pending) }
            runCatching { pending.cancel() }
        }

        remove(context, id)
    }

    /** Reboot ke baad. Jo waqt nikal chuka hai use chup-chaap gira dete hain. */
    fun rescheduleAll(context: Context) {
        ensureChannel(context)

        val now = System.currentTimeMillis()
        val alive = all(context).filter { it.at > now }
        alive.forEach { setAlarm(context, it) }
        save(context, alive)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "E.V reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Jo aapne yaad dilane ko kaha tha"
            enableVibration(true)
            setShowBadge(true)
        }

        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    /** "Kal subah 8:00 baje yaad dila dunga: dawai leni hai" */
    fun confirmation(at: Long, text: String): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = at }

        val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val isTomorrow = tomorrow.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

        val clock = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(at))
        val day = when {
            sameDay -> "Aaj"
            isTomorrow -> "Kal"
            else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(at))
        }

        return day + " " + clock + " baje yaad dila dunga: " + text
    }

    // --------------------------------------------------------------- private

    /**
     * Alarm set karna — teen tareeke, sabse bharosemand pehle.
     *
     * **Yahi wo bug tha jiski wajah se "2 minute baad yaad dilana" kaam nahi
     * karta tha.** Pehle exact-alarm permission na hone par
     * `setAndAllowWhileIdle` lagta tha, aur Android us tarah ke alarm ko **9
     * minute me ek baar se zyada nahi** bajata. Yani 2 minute wala reminder 10
     * ya 12 minute baad bajta tha, ya tab jab aap screen on karte the.
     *
     * `setAlarmClock` me ye dikkat hai hi nahi: use koi special permission
     * nahi chahiye, Doze use rok nahi sakta, aur wo hamesha theek waqt pe
     * bajta hai. Badle me status bar me ek chhota alarm ka nishan aa jata hai
     * — ye sauda faayde ka hai.
     */
    private fun setAlarm(context: Context, reminder: Reminder): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return false

        val pending = PendingIntent.getBroadcast(
            context,
            reminder.id,
            fireIntent(context, reminder.id, reminder.text),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val viaAlarmClock = runCatching {
            // Status bar ke nishan pe tap karne se E.V khulta hai.
            val show = PendingIntent.getActivity(
                context,
                reminder.id,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.at, show), pending)
            true
        }.getOrDefault(false)

        if (viaAlarmClock) return true

        // Kisi phone ne setAlarmClock rok diya to purane tareeke.
        return runCatching {
            if (canBeExact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.at, pending)
            }
            true
        }.getOrDefault(false)
    }

    private fun fireIntent(context: Context, id: Int, text: String): Intent =
        Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_TEXT, text)

    private fun nextId(existing: List<Reminder>): Int {
        var id = (System.currentTimeMillis() / 1000L).toInt()
        val used = existing.map { it.id }.toSet()
        while (id in used) id++
        return id
    }

    private fun save(context: Context, reminders: List<Reminder>) {
        val array = JSONArray()
        reminders.forEach { reminder ->
            array.put(
                JSONObject()
                    .put("id", reminder.id)
                    .put("at", reminder.at)
                    .put("text", reminder.text)
            )
        }
        prefs(context).edit().putString(KEY, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
