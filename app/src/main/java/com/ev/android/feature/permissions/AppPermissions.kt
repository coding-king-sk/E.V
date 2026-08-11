package com.ev.android.feature.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Ek runtime permission, uske saath wo baat jo user ko sach me chahiye:
 * ye kis kaam ke liye hai, aur na dene par kya hoga.
 */
data class EvPermission(
    val permission: String,
    val label: String,
    val why: String,
    /** Is SDK se pehle ye permission maangi hi nahi jaati. */
    val minSdk: Int = 0,
    /** Is SDK ke baad Android khud sambhal leta hai. */
    val maxSdk: Int = Int.MAX_VALUE,
)

/**
 * Saari permissions ek jagah.
 *
 * Yahan sirf wahi permissions hain jinke peeche app ka koi asli feature hai.
 * Storage wali pehle list me thi \u2014 wo sirf Android 9 aur usse purane pe lagti
 * thi, isliye nikal di gayi.
 */
object AppPermissions {

    /** Wo permissions jo Android runtime pe maangta hai. */
    private val all: List<EvPermission> = listOf(
        EvPermission(
            permission = Manifest.permission.RECORD_AUDIO,
            label = "Mic",
            why = "Iske bina E.V sun hi nahi sakta \u2014 na mic button, na \"Hey E.V\".",
        ),
        EvPermission(
            permission = Manifest.permission.POST_NOTIFICATIONS,
            label = "Notification",
            why = "Android background me mic tabhi chalne deta hai jab notification " +
                "dikhe. Isse aapko hamesha pata rahega ki mic on hai.",
            minSdk = Build.VERSION_CODES.TIRAMISU,
        ),
        EvPermission(
            permission = Manifest.permission.CAMERA,
            label = "Camera",
            why = "\"Photo lo\" aur \"video banao\" ke liye.",
        ),
        EvPermission(
            permission = Manifest.permission.READ_CONTACTS,
            label = "Contacts",
            why = "\"Rehan ko call lagao\" me naam se number dhoondhne ke liye.",
        ),
        EvPermission(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            label = "Location",
            why = "Sirf \"meri location batao\" ke liye. E.V location kabhi background " +
                "me nahi leta aur kahin bhejta bhi nahi.",
        ),
        EvPermission(
            permission = Manifest.permission.SEND_SMS,
            label = "SMS bhejna",
            why = "Na do to bhi chalega \u2014 bas E.V message type karke SMS app khol " +
                "dega, send aapko dabana padega.",
        ),
        EvPermission(
            permission = Manifest.permission.CALL_PHONE,
            label = "Call lagana",
            why = "Na do to bhi chalega \u2014 E.V dialer me number bhar dega, call " +
                "button aapko dabana padega.",
        ),
    )

    /** Is phone ke Android version pe jo permissions sach me lagti hain. */
    fun applicable(): List<EvPermission> = all.filter {
        Build.VERSION.SDK_INT >= it.minSdk && Build.VERSION.SDK_INT <= it.maxSdk
    }

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

    /** Jo abhi tak nahi mili \u2014 yahi maangni hai. */
    fun missing(context: Context): List<String> =
        applicable().map { it.permission }.filterNot { isGranted(context, it) }

    fun allGranted(context: Context): Boolean = missing(context).isEmpty()

    // ------------------------------------------------- special (settings wali)

    /**
     * Ye cheezein normal permission dialog se nahi milti \u2014 har ek ke liye
     * Android ki alag screen kholni padti hai.
     */
    fun batteryUnrestricted(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun exactAlarmAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun openAppSettings(context: Context) = open(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )

    fun openBatterySettings(context: Context) =
        open(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        open(
            context,
            Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    }

    fun openWriteSettings(context: Context) = open(
        context,
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )

    /**
     * Kuch phones (khaas kar MIUI/ColorOS) me ye screens missing hoti hain.
     * Aisi soorat me crash karne se behtar hai chupchap app settings khol dena.
     */
    private fun open(context: Context, intent: Intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (opened) return

        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
