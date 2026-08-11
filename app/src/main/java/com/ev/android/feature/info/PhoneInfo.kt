package com.ev.android.feature.info

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Jo cheezein phone khud jaanta hai aur poochne par bata sakta hai. */
enum class InfoKind { BATTERY, TIME, DATE, STORAGE }

/**
 * "Battery kitni hai", "time kya hua", "storage kitna bacha".
 *
 * In sab ke liye na internet chahiye na koi permission \u2014 Android khud
 * bata deta hai. Isliye ye AI ke paas bhejne wali baat hi nahi hai.
 */
object PhoneInfo {

    fun answer(context: Context, kind: InfoKind): String = when (kind) {
        InfoKind.BATTERY -> battery(context)
        InfoKind.TIME -> "Abhi " + format("h:mm a") + " baje hain"
        InfoKind.DATE -> "Aaj " + format("EEEE, d MMMM yyyy") + " hai"
        InfoKind.STORAGE -> storage()
    }

    private fun format(pattern: String): String =
        SimpleDateFormat(pattern, Locale.ENGLISH).format(Date())

    private fun battery(context: Context): String {
        // Battery ka status ek "sticky broadcast" hota hai: receiver null bhej
        // ke turant aakhri value mil jaati hai, register karne ki zaroorat nahi.
        val intent = context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ) ?: return "Battery ka pata nahi chal paya"

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return "Battery ka pata nahi chal paya"

        val percent = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        return if (charging) {
            "Battery $percent% hai aur charge ho rahi hai"
        } else {
            "Battery $percent% hai"
        }
    }

    private fun storage(): String {
        // Internal storage \u2014 wahi jagah jahan apps aur photos jaate hain.
        val stat = StatFs(Environment.getDataDirectory().path)
        val free = stat.availableBytes
        val total = stat.totalBytes
        if (total <= 0) return "Storage ka pata nahi chal paya"

        val usedPercent = ((total - free) * 100 / total).toInt()
        return size(free) + " khaali hai (" + size(total) + " me se, " +
            usedPercent + "% bhara hua)"
    }

    private fun size(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1.0) {
            String.format(Locale.ENGLISH, "%.1f GB", gb)
        } else {
            String.format(Locale.ENGLISH, "%.0f MB", bytes / 1024.0 / 1024.0)
        }
    }
}
