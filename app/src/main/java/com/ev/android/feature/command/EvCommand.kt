package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.media.MediaAction

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

    /** "rehan ko sms bhejo ki main aa raha hoon" */
    data class SendSms(val contactName: String, val message: String) : EvCommand

    /** "rehan ko call lagao" */
    data class CallContact(val contactName: String) : EvCommand

    /** "next gaana", "gaana roko", "10 second aage" */
    data class Media(val action: MediaAction) : EvCommand

    /** "ye gaana kaun sa hai" */
    data object IdentifySong : EvCommand

    /** "5 minute ka timer lagao" */
    data class Timer(val seconds: Int, val label: String?) : EvCommand

    /** "subah 7 baje alarm lagao" */
    data class Alarm(val hour: Int, val minute: Int) : EvCommand

    /**
     * "kal subah 8 baje yaad dilana ki dawai leni hai"
     *
     * Alarm se alag hai: alarm phone ki Clock app me jata hai aur sirf bajta
     * hai, jabki reminder E.V khud yaad rakhta hai aur waqt aane par bol ke
     * batata hai ki kya karna tha.
     *
     * @param at epoch millis — kab bajana hai
     */
    data class Reminder(val at: Long, val text: String) : EvCommand

    /** "torch on karo", "volume badhao", "screenshot lo" */
    data class Device(val action: DeviceAction) : EvCommand

    data class Unknown(val raw: String) : EvCommand
}
