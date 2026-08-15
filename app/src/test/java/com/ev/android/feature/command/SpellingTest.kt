package com.ev.android.feature.command

import com.ev.android.feature.screen.ScreenAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Recognizer ki spelling se command na tootne paye.
 *
 * Asli shikayat: "screen per kya likha hai" bolne pe phone ki "Screen Lock"
 * app khul jaati thi — kyunki "per" kisi list me nahi tha aur command aakhir
 * me app kholne wale hisse tak pahunch jaati thi.
 */
class SpellingTest {

    private fun parse(text: String): EvCommand = CommandParser.parse(text, emptyList())

    @Test
    fun `per bhi pe ki tarah chalta hai`() {
        assertEquals(
            EvCommand.Screen(ScreenAction.READ),
            parse("screen per kya likha hai"),
        )
    }

    @Test
    fun `screen pe wala purana jumla bhi chalta hai`() {
        assertEquals(
            EvCommand.Screen(ScreenAction.READ),
            parse("screen pe kya likha hai"),
        )
    }

    @Test
    fun `whatsapp per bhejo message hi banta hai`() {
        assertEquals(
            EvCommand.SendWhatsApp(contactName = "rehan", message = "aa raha hoon"),
            parse("whatsapp per rehan ko bhejo ki aa raha hoon"),
        )
    }

    @Test
    fun `neeche karo scroll hi hai`() {
        assertEquals(
            EvCommand.Screen(ScreenAction.SCROLL_DOWN),
            parse("neeche karo"),
        )
    }

    @Test
    fun `upar karo scroll hi hai`() {
        assertEquals(
            EvCommand.Screen(ScreenAction.SCROLL_UP),
            parse("upar karo"),
        )
    }
}
