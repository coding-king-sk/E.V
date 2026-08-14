package com.ev.android.feature.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "likho" wale vaakya.
 *
 * Ye shabd teen jagah chalta hai — typing, message aur note. Pehle typing wali
 * branch sabse aage thi, isliye "rehan ko likho ki…" message hi nahi banta tha
 * aur "note likho…" note nahi banta tha.
 */
class TypeVsMessageTest {

    private fun parse(text: String): EvCommand = CommandParser.parse(text, emptyList())

    /** Regression: message ki jagah typing ho jaati thi. */
    @Test
    fun `naam ke saath likho message banta hai`() {
        assertEquals(
            EvCommand.SendWhatsApp(contactName = "rehan", message = "main aa raha hoon"),
            parse("rehan ko likho ki main aa raha hoon"),
        )
    }

    /** Regression: note ki jagah typing ho jaati thi. */
    @Test
    fun `note likho note banta hai`() {
        assertEquals(EvCommand.Note("khana khana hai"), parse("note likho khana khana hai"))
    }

    /** "type" wala raasta pehle jaisa hi chalta hai. */
    @Test
    fun `app pe type karo typing hi rehta hai`() {
        val command = parse("instagram pe type karo hello bhai")
        assertTrue(command is EvCommand.TypeText)
        assertEquals("hello bhai", (command as EvCommand.TypeText).text)
    }
}
