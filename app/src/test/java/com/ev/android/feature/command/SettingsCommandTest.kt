package com.ev.android.feature.command

import com.ev.android.feature.device.DeviceAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * "Settings kholo" kiski settings hai.
 *
 * Phone ki settings sirf tab kholni hai jab kisi app ka naam saath me na ho.
 * "whatsapp settings kholo" me user app ke andar ki settings chahta hai —
 * wahan phone ki settings khol dena galat jawab tha.
 */
class SettingsCommandTest {

    private fun parse(text: String) = CommandParser.parse(text, emptyList())

    @Test
    fun `settings kholo phone ki settings kholta hai`() {
        assertEquals(EvCommand.Device(DeviceAction.SETTINGS), parse("settings kholo"))
    }

    @Test
    fun `phone ki settings kholo bhi chalta hai`() {
        assertEquals(EvCommand.Device(DeviceAction.SETTINGS), parse("phone ki settings kholo"))
    }

    @Test
    fun `app ka naam saath ho to phone ki settings nahi khulti`() {
        assertNotEquals(
            EvCommand.Device(DeviceAction.SETTINGS),
            parse("whatsapp settings kholo"),
        )
    }
}
