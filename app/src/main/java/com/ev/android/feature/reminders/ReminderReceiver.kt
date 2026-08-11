package com.ev.android.feature.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ev.android.MainActivity
import com.ev.android.feature.tts.Speaker
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reminder ka waqt aa gaya.
 *
 * Notification bhi dikhate hain aur bol ke bhi batate hain — kyunki agar phone
 * jeb me hai to notification dikhega nahi, aur agar phone silent hai to awaz
 * nahi aayegi. Dono saath me safe hai.
 *
 * Bolne wala hissa pehle chalta hi nahi tha: `onReceive` khatam hote hi Android
 * process ko marne ke liye azaad ho jata hai, aur TTS engine ko taiyaar hone me
 * ek-do second lagte hain — utni der me hi process chala jata tha. Ab
 * `goAsync()` se receiver bolne tak zinda rehta hai.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reboot ke baad Android saare alarms bhool jata hai.
            Reminders.rescheduleAll(context)
            return
        }

        val id = intent.getIntExtra(Reminders.EXTRA_ID, 0)
        val text = intent.getStringExtra(Reminders.EXTRA_TEXT).orEmpty()
        if (text.isBlank()) return

        Reminders.ensureChannel(context)

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, Reminders.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("\u23F0 E.V ka reminder")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Android 8 se pehle channel nahi hota, isliye awaaz aur vibration
            // yahin se aati hai.
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0L, 400L, 200L, 400L))
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // Notification permission user ne baad me hata di ho to yahan
        // SecurityException aata hai — uske liye app crash karna theek nahi.
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        // List se hataana zaroori hai, warna reboot pe purana reminder dobara
        // set ho jata.
        Reminders.remove(context, id)

        speak(context, text)
    }

    /**
     * Bolne tak receiver ko zinda rakho.
     *
     * `finish()` do baar bulane pe Android exception phenkta hai, isliye ek
     * flag rakha hai: jo pehle aaya — bolna khatam ya 9 second ka timeout —
     * wahi band karega.
     */
    private fun speak(context: Context, text: String) {
        val pending = goAsync()
        val done = AtomicBoolean(false)

        val finish = {
            if (done.compareAndSet(false, true)) {
                runCatching { pending.finish() }
                Unit
            }
        }

        // Android broadcast ko zyada der zinda nahi rehne deta (~10 second).
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, ALIVE_MS)

        runCatching {
            Speaker.init(context)
            Speaker.speak(text) { finish() }
        }.onFailure { finish() }
    }

    private companion object {
        const val ALIVE_MS = 9_000L
    }
}
