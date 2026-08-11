package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction
import com.ev.android.feature.info.Calculator
import com.ev.android.feature.info.InfoKind
import com.ev.android.feature.settings.EvSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wo saare jumle jo asli phone pe fail hue the.
 *
 * Ye test isliye hai ki inme se koi bug dobara aaye to CI pakde, user nahi.
 * Har test ka naam wahi hai jo screenshot me dikha tha.
 */
class HinglishBugsTest {

    private fun parse(text: String) = CommandParser.parse(text, emptyList())

    // -------------------------------------------------- "pe" vs "per"

    @Test
    fun `youtube per paisa song lagao`() {
        val command = parse("YouTube per Paisa song lagao")
        assertTrue(command is EvCommand.PlayMedia)
        assertEquals("paisa song", (command as EvCommand.PlayMedia).query)
    }

    @Test
    fun `whatsapp per armaan ko hay bhejo`() {
        val command = parse("WhatsApp per armaan ko hay bhejo")
        assertTrue(command is EvCommand.SendWhatsApp)
        command as EvCommand.SendWhatsApp
        assertEquals("armaan", command.contactName)
        assertEquals("hay", command.message)
    }

    // -------------------------------------------------- awaaz / volume

    @Test
    fun `awaaz thodi jyada karo`() {
        val command = parse("Awaaz Thodi Jyada karo")
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_UP), command)
    }

    @Test
    fun `awaaz thodi dhime karo`() {
        val command = parse("Awaaz Thodi dhime karo")
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_DOWN), command)
    }

    @Test
    fun `volume 100 percent karo`() {
        val command = parse("volume 100% karo")
        assertEquals(EvCommand.Device(DeviceAction.VOLUME_SET, 100), command)
    }

    @Test
    fun `brightness 60 percent karo`() {
        val command = parse("brightness 60% karo")
        assertEquals(EvCommand.Device(DeviceAction.BRIGHTNESS_SET, 60), command)
    }

    // -------------------------------------------------- timer / camera

    @Test
    fun `2 minut ka time lagao`() {
        val command = parse("2 minut ka time lagao")
        assertEquals(EvCommand.Timer(120, null), command)
    }

    @Test
    fun `5 second ki video bnao`() {
        val command = parse("5 second ki video bnao")
        assertTrue(command is EvCommand.RecordVideo)
        assertEquals(5, (command as EvCommand.RecordVideo).seconds)
    }

    @Test
    fun `recording chalu karo bhi video banata hai`() {
        assertTrue(parse("recording chalu karo") is EvCommand.RecordVideo)
    }

    // -------------------------------------------------- sawaal ke jawab

    @Test
    fun `time kya hua hai jawab deta hai timer nahi banata`() {
        assertEquals(EvCommand.Info(InfoKind.TIME), parse("time kya hua hai"))
    }

    @Test
    fun `battery kitni hai`() {
        assertEquals(EvCommand.Info(InfoKind.BATTERY), parse("battery kitni hai"))
    }

    @Test
    fun `storage kitna baki hai`() {
        assertEquals(EvCommand.Info(InfoKind.STORAGE), parse("storage kitna baki hai"))
    }

    // -------------------------------------------------- note / hisaab

    @Test
    fun `note karo khana khana hai`() {
        assertEquals(EvCommand.Note("khana khana hai"), parse("note karo khana khana hai"))
    }

    @Test
    fun `1500 ka 18 percent`() {
        assertTrue(parse("1500 ka 18% kitna hota hai") is EvCommand.Calculate)
        assertTrue(Calculator.evaluate("1500 ka 18% kitna hota hai")!!.endsWith("270"))
    }

    @Test
    fun `hisaab nahi hai to null`() {
        assertNull(Calculator.evaluate("whatsapp kholo"))
    }

    // -------------------------------------------------- search / call / apps

    @Test
    fun `google pe search karo`() {
        val command = parse("google pe sachin tendulkar search karo")
        assertEquals(EvCommand.WebSearch("sachin tendulkar"), command)
    }

    @Test
    fun `whatsapp per kais ko call karo`() {
        val command = parse("whatsapp per kais ko call karo")
        assertEquals(EvCommand.WhatsAppCall("kais", video = false), command)
    }

    @Test
    fun `whatsapp pe video call`() {
        val command = parse("whatsapp pe kais ko video call karo")
        assertEquals(EvCommand.WhatsAppCall("kais", video = true), command)
    }

    @Test
    fun `gallery kholo`() {
        val command = parse("gallery kholo")
        assertTrue(command is EvCommand.OpenApp)
        assertEquals("Gallery", (command as EvCommand.OpenApp).target.label)
    }

    // -------------------------------------------------- aliases

    @Test
    fun `alias list padhi jaati hai`() {
        val aliases = EvSettings.parseAliases("case = Kais\na man : Armaan\nkachra line")
        assertEquals("Kais", aliases["case"])
        assertEquals("Armaan", aliases["a man"])
        assertEquals(2, aliases.size)
    }
}
