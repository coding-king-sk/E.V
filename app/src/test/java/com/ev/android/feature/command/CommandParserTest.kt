package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.media.MediaAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CommandParser ke tests.
 *
 * Yahan installed apps ki list jaan bujh ke khali rakhi hai — hum sirf ye check
 * kar rahe hain ki Hinglish sentence ka *shape* sahi samjha ja raha hai.
 */
class CommandParserTest {

    private fun parse(text: String): EvCommand = CommandParser.parse(text, emptyList())

    // ---------------------------------------------------------------- call

    @Test
    fun `naam ke saath call lagta hai`() {
        assertEquals(EvCommand.CallContact("rehan"), parse("rehan ko call lagao"))
        assertEquals(EvCommand.CallContact("mummy"), parse("mummy ko phone karo"))
    }

    /** Regression: "call of duty kholo" dialer khol deta tha. */
    @Test
    fun `game ka naam call nahi banta`() {
        assertTrue(parse("call of duty kholo") !is EvCommand.CallContact)
        assertTrue(parse("dial pad kholo") !is EvCommand.CallContact)
        assertTrue(parse("missed call wali app kholo") !is EvCommand.CallContact)
    }

    // ------------------------------------------------------------- message

    @Test
    fun `sms bolne par sms jata hai`() {
        assertEquals(
            EvCommand.SendSms(contactName = "rehan", message = "main aa raha hoon"),
            parse("rehan ko sms bhejo ki main aa raha hoon"),
        )
    }

    @Test
    fun `default me whatsapp jata hai`() {
        assertEquals(
            EvCommand.SendWhatsApp(contactName = "rehan", message = "main aa raha hoon"),
            parse("rehan ko bolo ki main aa raha hoon"),
        )
    }

    // --------------------------------------------------------------- media

    @Test
    fun `media controls`() {
        assertEquals(EvCommand.Media(MediaAction.NEXT), parse("next gaana"))
        assertEquals(EvCommand.Media(MediaAction.PAUSE), parse("gaana roko"))
        assertEquals(EvCommand.Media(MediaAction.PREVIOUS), parse("pichla gaana"))
    }

    @Test
    fun `gaana pehchanna`() {
        assertEquals(EvCommand.IdentifySong, parse("ye gaana kaun sa hai"))
    }

    /** "paisa gaana chalu karo" resume nahi, naya gaana hai. */
    @Test
    fun `naam wala gaana resume nahi banta`() {
        assertTrue(parse("paisa gaana chalu karo") !is EvCommand.Media)
    }

    // ------------------------------------------------------- timer o alarm

    @Test
    fun `timer banta hai`() {
        assertEquals(EvCommand.Timer(300, null), parse("5 minute ka timer lagao"))
        assertEquals(EvCommand.Timer(30, null), parse("30 second ka timer lagao"))
    }

    @Test
    fun `alarm banta hai`() {
        assertEquals(EvCommand.Alarm(7, 0), parse("subah 7 baje ka alarm lagao"))
        assertEquals(EvCommand.Alarm(19, 0), parse("shaam 7 baje ka alarm lagao"))
        assertEquals(EvCommand.Alarm(0, 0), parse("raat 12 baje ka alarm lagao"))
    }

    // -------------------------------------------------------------- device

    @Test
    fun `torch commands`() {
        assertEquals(EvCommand.Device(DeviceAction.TORCH_ON), parse("torch on karo"))
        assertEquals(EvCommand.Device(DeviceAction.TORCH_OFF), parse("torch band karo"))
    }

    @Test
    fun `volume aur brightness`() {
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_UP), parse("volume badhao"))
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_DOWN), parse("awaz kam karo"))
        assertEquals(EvCommand.Device(DeviceAction.BRIGHTNESS_MAX), parse("brightness full karo"))
    }

    @Test
    fun `screenshot aur lock`() {
        assertEquals(EvCommand.Device(DeviceAction.SCREENSHOT), parse("screenshot lo"))
        assertEquals(EvCommand.Device(DeviceAction.LOCK_SCREEN), parse("screen lock karo"))
    }

    /** Ambiguous words bina modifier ke device command nahi bante. */
    @Test
    fun `sound of music device command nahi hai`() {
        assertTrue(parse("sound of music lagao") !is EvCommand.Device)
    }

    // ---------------------------------------------------------------- misc

    @Test
    fun `khali input unknown hai`() {
        assertTrue(parse("   ") is EvCommand.Unknown)
    }

    @Test
    fun `youtube pe gaana lagao`() {
        val command = parse("youtube pe paisa song lagao")
        assertTrue(command is EvCommand.PlayMedia)
        assertEquals("YouTube", (command as EvCommand.PlayMedia).target.label)
    }
}
