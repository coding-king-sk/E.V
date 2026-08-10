package com.ev.android.feature.reminders

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ev.android.MainActivity
import com.ev.android.feature.tts.Speaker

/**
 * Reminder ka waqt aa gaya.
 *
 * Notification bhi dikhate hain aur bol ke bhi batate hain — kyunki agar phone
 * jeb me hai to notification dikhega nahi, aur agar phone silent hai to awaz
 * nahi aayegi. Dono saath me safe hai.
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
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // Notification permission user ne baad me hata di ho to yahan
        // SecurityException aata hai — uske liye app crash karna theek nahi.
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }

        runCatching {
            Speaker.init(context)
            Speaker.speak(text)
        }

        Reminders.remove(context, id)
    }
}
