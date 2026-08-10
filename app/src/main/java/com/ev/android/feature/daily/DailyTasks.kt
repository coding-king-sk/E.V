package com.ev.android.feature.daily

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.ev.android.feature.launcher.AppLauncher

/**
 * Rozmarra ke kaam \u2014 timer aur alarm.
 *
 * Android ke apne AlarmClock intents use karte hain, isliye ye phone ki default
 * Clock app me set hote hain, koi permission nahi lagti, aur EXTRA_SKIP_UI se
 * bina kuch tap kiye seedha set ho jate hain.
 */
object DailyTasks {

    fun setTimer(context: Context, seconds: Int, label: String?): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        return AppLauncher.startIntent(context, intent)
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String?): Boolean {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (!label.isNullOrBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        return AppLauncher.startIntent(context, intent)
    }

    fun formatDuration(seconds: Int): String = when {
        seconds % 3600 == 0 -> (seconds / 3600).toString() + " ghante"
        seconds >= 60 && seconds % 60 == 0 -> (seconds / 60).toString() + " minute"
        else -> seconds.toString() + " second"
    }

    fun formatTime(hour: Int, minute: Int): String {
        val padded = if (minute < 10) "0" + minute else minute.toString()
        return hour.toString() + ":" + padded
    }
}
