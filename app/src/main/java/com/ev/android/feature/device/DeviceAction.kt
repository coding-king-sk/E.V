package com.ev.android.feature.device

enum class DeviceAction {
    TORCH_ON,
    TORCH_OFF,
    TORCH_TOGGLE,

    WIFI_ON,
    WIFI_OFF,
    WIFI_PANEL,

    BLUETOOTH,
    MOBILE_DATA,
    AIRPLANE_MODE,

    VOLUME_UP,
    VOLUME_DOWN,
    VOLUME_MAX,
    VOLUME_MUTE,

    BRIGHTNESS_UP,
    BRIGHTNESS_DOWN,
    BRIGHTNESS_MAX,
    BRIGHTNESS_MIN,

    RINGER_SILENT,
    RINGER_VIBRATE,
    RINGER_NORMAL,

    SCREENSHOT,
    LOCK_SCREEN,
    HOME,
    BACK,
    RECENTS,
    NOTIFICATIONS,

    SETTINGS,
}

data class DeviceResult(val ok: Boolean, val message: String)
