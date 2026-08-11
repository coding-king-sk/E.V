package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Jo bhi Hinglish command phone pe fail hui, uska ek test yahan aata hai.
 *
 * Matlab: ek baar theek hone ke baad wo dobara nahi toot sakti \u2014 CI pehle
 * hi pakad legi. Ye saare cases user ke phone se aaye hain.
 */
class HinglishBugsTest {

    private fun parse(text: String): EvCommand = CommandParser.parse(text, emptyList())

    // Google ka recognizer "pe" ko "per" likhta hai \u2014 dono ek hi cheez hain.

    @Test
    fun `youtube per bhi youtube pe hi hai`() {
        val command = parse("youtube per paisa song lagao")
        assertTrue(command.toString(), command is EvCommand.PlayMedia)
        assertEquals("paisa song", (command as EvCommand.PlayMedia).query)
    }

    @Test
    fun `whatsapp per bhejo to naam sahi nikle`() {
        val command = parse("whatsapp per armaan ko hay bhejo")
        assertTrue(command.toString(), command is EvCommand.SendWhatsApp)
        command as EvCommand.SendWhatsApp
        assertEquals("armaan", command.contactName)
        assertEquals("hay", command.message)
    }

    // Awaaz \u2014 roz ke jumle.

    @Test
    fun `awaaz thodi jyada karo`() {
        val command = parse("awaaz thodi jyada karo")
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_UP), command)
    }

    @Test
    fun `awaaz thodi dhime karo`() {
        val command = parse("awaaz thodi dhime karo")
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_DOWN), command)
    }

    @Test
    fun `volume percent set hota hai`() {
        assertEquals(
            EvCommand.Device(DeviceAction.VOLUME_SET, 100),
            parse("volume 100% karo"),
        )
    }

    @Test
    fun `brightness percent set hota hai`() {
        assertEquals(
            EvCommand.Device(DeviceAction.BRIGHTNESS_SET, 60),
            parse("brightness 60% karo"),
        )
    }

    // Timer \u2014 log "timer" ki jagah "time" bhi bolte hain.

    @Test
    fun `do minut ka time bhi timer hai`() {
        val command = parse("2 minut ka time lagao")
        assertEquals(EvCommand.Timer(120, null), command)
    }

    @Test
    fun `time akela timer nahi banta`() {
        // Bina duration ke "time" ka matlab timer nahi hota.
        val command = parse("time kya hua hai")
        assertTrue(command.toString(), command is EvCommand.Unknown)
    }

    // Camera.

    @Test
    fun `video bnao bhi chalta hai`() {
        val command = parse("5 second ki video bnao")
        assertTrue(command.toString(), command is EvCommand.RecordVideo)
        assertEquals(5, (command as EvCommand.RecordVideo).seconds)
    }
}
