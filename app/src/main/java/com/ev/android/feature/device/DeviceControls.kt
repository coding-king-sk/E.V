package com.ev.android.feature.device

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.ev.android.feature.accessibility.EvAccessibilityService
import com.ev.android.feature.launcher.AppLauncher

/**
 * Phone ke hardware/system toggles.
 *
 * Android ke rules yahan bahut sakht hain, to har control ke 2 raste hain:
 *  - Jo app khud kar sakti hai (torch, volume, ringer, brightness) \u2014 direct.
 *  - Jo Android app ko karne hi nahi deta (WiFi on Android 10+, Bluetooth,
 *    mobile data, airplane mode) \u2014 wahan seedha wo wala settings panel khulta
 *    hai, taaki ek tap me ho jaye.
 */
object DeviceControls {

    @Volatile
    private var torchOn = false

    fun isTorchOn(): Boolean = torchOn

    fun run(context: Context, action: DeviceAction): DeviceResult = when (action) {
        DeviceAction.TORCH_ON -> setTorch(context, true)
        DeviceAction.TORCH_OFF -> setTorch(context, false)
        DeviceAction.TORCH_TOGGLE -> setTorch(context, !torchOn)

        DeviceAction.WIFI_ON -> setWifi(context, true)
        DeviceAction.WIFI_OFF -> setWifi(context, false)
        DeviceAction.WIFI_PANEL -> setWifi(context, null)

        DeviceAction.BLUETOOTH -> openSettingsScreen(
            context,
            Settings.ACTION_BLUETOOTH_SETTINGS,
            "Bluetooth settings khol di",
        )

        DeviceAction.MOBILE_DATA -> mobileData(context)
        DeviceAction.AIRPLANE_MODE -> openSettingsScreen(
            context,
            Settings.ACTION_AIRPLANE_MODE_SETTINGS,
            "Airplane mode settings khol di",
        )

        DeviceAction.VOLUME_UP -> adjustVolume(context, AudioManager.ADJUST_RAISE, "Volume badha diya")
        DeviceAction.VOLUME_DOWN -> adjustVolume(context, AudioManager.ADJUST_LOWER, "Volume kam kar diya")
        DeviceAction.VOLUME_MAX -> maxVolume(context)
        DeviceAction.VOLUME_MUTE -> muteVolume(context)

        DeviceAction.BRIGHTNESS_UP -> setBrightness(context, delta = 51)
        DeviceAction.BRIGHTNESS_DOWN -> setBrightness(context, delta = -51)
        DeviceAction.BRIGHTNESS_MAX -> setBrightness(context, absolute = 255)
        DeviceAction.BRIGHTNESS_MIN -> setBrightness(context, absolute = 10)

        DeviceAction.RINGER_SILENT -> setRinger(context, AudioManager.RINGER_MODE_SILENT, "Silent mode on")
        DeviceAction.RINGER_VIBRATE -> setRinger(context, AudioManager.RINGER_MODE_VIBRATE, "Vibrate mode on")
        DeviceAction.RINGER_NORMAL -> setRinger(context, AudioManager.RINGER_MODE_NORMAL, "Ringtone mode on")

        DeviceAction.SCREENSHOT -> screenshot()
        DeviceAction.LOCK_SCREEN -> lockScreen()
        DeviceAction.HOME -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Home")
        DeviceAction.BACK -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Back")
        DeviceAction.RECENTS -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recent apps")
        DeviceAction.NOTIFICATIONS -> global(
            AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            "Notifications khol diye",
        )

        DeviceAction.SETTINGS -> openSettingsScreen(
            context,
            Settings.ACTION_SETTINGS,
            "Settings khol di",
        )
    }

    // ---------------------------------------------------------------- torch

    private fun setTorch(context: Context, enable: Boolean): DeviceResult {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return DeviceResult(false, "Camera service nahi mila")

        return runCatching {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return DeviceResult(false, "Is phone me flash nahi hai")

            manager.setTorchMode(cameraId, enable)
            torchOn = enable
            DeviceResult(true, if (enable) "Torch on \uD83D\uDD26" else "Torch off")
        }.getOrElse {
            DeviceResult(false, "Torch control nahi ho paya")
        }
    }

    // ----------------------------------------------------------------- wifi

    private fun setWifi(context: Context, enable: Boolean?): DeviceResult {
        // Android 10 (Q) se aage koi bhi app WiFi khud on/off nahi kar sakti.
        // Google ne uske badle ek quick panel diya hai.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val opened = AppLauncher.startIntent(context, Intent(Settings.Panel.ACTION_WIFI))
            return DeviceResult(
                opened,
                if (opened) "Android 10+ pe WiFi app se toggle nahi hota \u2014 panel khol diya, ek tap me on/off"
                else "WiFi panel khul nahi paya",
            )
        }

        if (enable == null) {
            return openSettingsScreen(context, Settings.ACTION_WIFI_SETTINGS, "WiFi settings khol di")
        }

        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return DeviceResult(false, "WiFi service nahi mila")

        return runCatching {
            @Suppress("DEPRECATION")
            wifi.isWifiEnabled = enable
            DeviceResult(true, if (enable) "WiFi on" else "WiFi off")
        }.getOrElse {
            openSettingsScreen(context, Settings.ACTION_WIFI_SETTINGS, "WiFi settings khol di")
        }
    }

    private fun mobileData(context: Context): DeviceResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val opened = AppLauncher.startIntent(
                context,
                Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY),
            )
            if (opened) return DeviceResult(true, "Internet panel khol diya")
        }
        return openSettingsScreen(
            context,
            Settings.ACTION_WIRELESS_SETTINGS,
            "Network settings khol di",
        )
    }

    // --------------------------------------------------------------- volume

    private fun audioManager(context: Context): AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private fun adjustVolume(context: Context, direction: Int, message: String): DeviceResult {
        val audio = audioManager(context) ?: return DeviceResult(false, "Audio service nahi mila")
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        return DeviceResult(true, message)
    }

    private fun maxVolume(context: Context): DeviceResult {
        val audio = audioManager(context) ?: return DeviceResult(false, "Audio service nahi mila")
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, AudioManager.FLAG_SHOW_UI)
        return DeviceResult(true, "Volume full \uD83D\uDD0A")
    }

    private fun muteVolume(context: Context): DeviceResult {
        val audio = audioManager(context) ?: return DeviceResult(false, "Audio service nahi mila")
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
        return DeviceResult(true, "Volume mute")
    }

    private fun setRinger(context: Context, mode: Int, message: String): DeviceResult {
        val notifications =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        // Silent/vibrate ke liye Android "Do Not Disturb access" maangta hai.
        if (notifications != null && !notifications.isNotificationPolicyAccessGranted) {
            AppLauncher.startIntent(
                context,
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
            )
            return DeviceResult(false, "Ek baar E.V ko 'Do Not Disturb' access de do")
        }

        val audio = audioManager(context) ?: return DeviceResult(false, "Audio service nahi mila")
        return runCatching {
            audio.ringerMode = mode
            DeviceResult(true, message)
        }.getOrElse {
            DeviceResult(false, "Ringer mode change nahi ho paya")
        }
    }

    // ----------------------------------------------------------- brightness

    private fun setBrightness(
        context: Context,
        delta: Int? = null,
        absolute: Int? = null,
    ): DeviceResult {
        // Brightness ek "special" permission hai \u2014 popup se nahi, settings se milti hai.
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
            AppLauncher.startIntent(context, intent)
            return DeviceResult(false, "Ek baar E.V ko 'Modify system settings' allow kar do")
        }

        return runCatching {
            val resolver = context.contentResolver

            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )

            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val target = (absolute ?: (current + (delta ?: 0))).coerceIn(1, 255)

            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)

            val percent = (target * 100) / 255
            DeviceResult(true, "Brightness $percent%")
        }.getOrElse {
            DeviceResult(false, "Brightness change nahi ho paya")
        }
    }

    // ------------------------------------------------- accessibility actions

    private fun global(action: Int, okMessage: String): DeviceResult =
        if (EvAccessibilityService.performGlobal(action)) {
            DeviceResult(true, okMessage)
        } else {
            DeviceResult(false, "Iske liye E.V ki Accessibility service on karni padegi")
        }

    private fun screenshot(): DeviceResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return DeviceResult(false, "Screenshot ke liye Android 11+ chahiye")
        }
        return global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT, "Screenshot le liya")
    }

    private fun lockScreen(): DeviceResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return DeviceResult(false, "Screen lock ke liye Android 9+ chahiye")
        }
        return global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN, "Screen lock \uD83D\uDD12")
    }

    // ------------------------------------------------------------- settings

    private fun openSettingsScreen(
        context: Context,
        action: String,
        message: String,
    ): DeviceResult {
        val opened = AppLauncher.startIntent(context, Intent(action))
        return DeviceResult(opened, if (opened) message else "Ye screen khul nahi payi")
    }
}
