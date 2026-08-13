package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.info.InfoKind
import com.ev.android.feature.media.MediaAction
import com.ev.android.feature.screen.ScreenAction

/**
 * Ek app jise E.V khol sakta hai.
 *
 * @param packageName null ho sakta hai agar app phone me mili hi nahi
 * @param webFallbackUrl app na ho to browser me kya kholna hai
 */
data class AppTarget(
    val label: String,
    val packageName: String?,
    val webFallbackUrl: String? = null,
)

/**
 * User ne jo bola, use ek saaf-suthre kaam me badal ke rakhna.
 *
 * Parser sirf ye banata hai; chalata [CommandExecutor] hai. Isi alag-alag rakhne
 * ki wajah se parser ke tests bina phone ke, plain JVM pe chal jate hain.
 */
sealed interface EvCommand {

    /** \"whatsapp kholo\" */
    data class OpenApp(val target: AppTarget) : EvCommand

    /** \"youtube pe paisa song lagao\" */
    data class PlayMedia(val query: String, val target: AppTarget) : EvCommand

    /** \"youtube pe cricket search karo\" */
    data class SearchInApp(val query: String, val target: AppTarget) : EvCommand

    /** \"google pe sachin tendulkar search karo\" */
    data class WebSearch(val query: String) : EvCommand

    /** \"rehan ko bolo ki main aa raha hoon\" */
    data class SendWhatsApp(val contactName: String?, val message: String) : EvCommand

    /** \"rehan ko sms bhejo ki aa raha hoon\" */
    data class SendSms(val contactName: String, val message: String) : EvCommand

    /** \"rehan ko call lagao\" */
    data class CallContact(val contactName: String) : EvCommand

    /** \"whatsapp pe rehan ko call karo\" */
    data class WhatsAppCall(val contactName: String, val video: Boolean) : EvCommand

    /** \"next gaana\", \"gaana roko\" */
    data class Media(val action: MediaAction) : EvCommand

    /** \"ye gaana kaun sa hai\" */
    data object IdentifySong : EvCommand

    /** \"5 minute ka timer lagao\" */
    data class Timer(val seconds: Int, val label: String?) : EvCommand

    /** \"subah 7 baje alarm lagao\" */
    data class Alarm(val hour: Int, val minute: Int) : EvCommand

    /** \"kal subah 8 baje yaad dilana ki dawai leni hai\" */
    data class Reminder(val at: Long, val text: String) : EvCommand

    /** \"note karo khana khana hai\" */
    data class Note(val text: String) : EvCommand

    /** \"battery kitni hai\", \"time kya hua\", \"storage kitna bacha\" */
    data class Info(val kind: InfoKind) : EvCommand

    /** \"meri location batao\" */
    data object WhereAmI : EvCommand

    /**
     * \"aaj mausam kaisa hai\", \"kal baarish hogi kya\"
     *
     * @param dayOffset 0 = aaj, 1 = kal, 2 = parso
     */
    data class Weather(val dayOffset: Int) : EvCommand

    /** \"1500 ka 18% kitna hota hai\" */
    data class Calculate(val expression: String) : EvCommand

    /**
     * \"torch on karo\", \"volume 60% karo\"
     *
     * @param level sirf VOLUME_SET / BRIGHTNESS_SET ke liye \u2014 0 se 100 percent.
     */
    data class Device(val action: DeviceAction, val level: Int? = null) : EvCommand

    /**
     * \"photo lo\" / \"selfie lo\"
     *
     * Camera khulta hai aur photo khud-ba-khud click ho jati hai \u2014 user ko
     * shutter dabane ki zaroorat nahi.
     */
    data class TakePhoto(val front: Boolean) : EvCommand

    /** \"30 second ka video banao\" \u2014 recording khud shuru aur khud band. */
    data class RecordVideo(val front: Boolean, val seconds: Int) : EvCommand

    /**
     * \"instagram pe type karo hello bhai\"
     *
     * App khulti hai aur uske text box me likh diya jata hai. [target] null ho
     * to jo app abhi khuli hai usi me type hota hai.
     */
    data class TypeText(val text: String, val target: AppTarget?) : EvCommand

    /**
     * \"screen pe kya likha hai\", \"screenshot lo\"
     *
     * Screen padhna Accessibility se hota hai, isliye ye tabhi chalta hai jab
     * user ne E.V ko Accessibility me on kiya ho.
     */
    data class Screen(val action: ScreenAction) : EvCommand

    /**
     * \"torch on karo aur whatsapp kholo\"
     *
     * Ek hi vaakya me kai kaam. Ek ke baad ek chalte hain, beech me thoda gap
     * rakh ke \u2014 warna doosri app pehli ke khulne se pehle hi launch ho jati hai.
     */
    data class Multi(val commands: List<EvCommand>) : EvCommand

    /** Kuch samajh nahi aaya \u2014 aage AI koshish karega. */
    data class Unknown(val raw: String) : EvCommand
}
