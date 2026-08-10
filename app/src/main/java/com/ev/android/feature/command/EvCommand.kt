package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction

/** An app (or web target) that a command should act on. */
data class AppTarget(
    val label: String,
    val packageName: String?,
    val webFallbackUrl: String? = null,
)

sealed interface EvCommand {
    /** "whatsapp kholo" */
    data class OpenApp(val target: AppTarget) : EvCommand

    /** "youtube pe paisa song lagao" -> resolve first result + autoplay */
    data class PlayMedia(val query: String, val target: AppTarget) : EvCommand

    /** "youtube pe cricket search karo" -> just show results */
    data class SearchInApp(val query: String, val target: AppTarget) : EvCommand

    /** "rehan ko whatsapp pe bolo ki main aa raha hoon" */
    data class SendWhatsApp(val contactName: String?, val message: String) : EvCommand

    /** "torch on karo", "volume badhao", "screenshot lo" */
    data class Device(val action: DeviceAction) : EvCommand

    data class Unknown(val raw: String) : EvCommand
}
