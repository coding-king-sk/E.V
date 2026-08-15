package com.ev.android.feature.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sawaal browser wale LLM ke paas jaayein, Google ke search page pe nahi.
 *
 * Shikayat yahi thi: "kuch bhi poocho to Google search khul jaata hai".
 * [EvCommand.Unknown] ka matlab hai "phone ka koi kaam nahi" — app aise
 * vaakya WebLlmBridge ko deti hai, jo Chrome me Gemini se jawab laata hai.
 */
class AskRoutingTest {

    private fun parse(text: String): EvCommand = CommandParser.parse(text, emptyList())

    @Test
    fun `aam sawaal browser wale llm ko jaata hai`() {
        assertTrue(parse("photosynthesis kya hai") is EvCommand.Unknown)
        assertTrue(parse("bharat ka pradhanmantri kaun hai") is EvCommand.Unknown)
        assertTrue(parse("sachin tendulkar ke bare me batao") is EvCommand.Unknown)
    }

    /** Bina app ke "search karo" ab YouTube pe nahi jaata. */
    @Test
    fun `bina app ke search bhi llm ko jaata hai`() {
        assertTrue(parse("quantum physics search karo") is EvCommand.Unknown)
        assertTrue(parse("cristiano ronaldo dhoondo") is EvCommand.Unknown)
    }

    /** "google" khud bola ho to Google hi khulta hai — wo user ki marzi hai. */
    @Test
    fun `google bola to google search hi hota hai`() {
        assertEquals(EvCommand.WebSearch("sachin"), parse("google pe sachin search karo"))
    }

    /** App ka naam ho to app ke andar hi search hota hai. */
    @Test
    fun `app ke andar search chalta rehta hai`() {
        val command = parse("youtube pe cricket highlights search karo")
        assertTrue(command is EvCommand.SearchInApp)
        assertEquals("YouTube", (command as EvCommand.SearchInApp).target.label)
    }

    /** Phone ke apne sawaal pehle jaise hi chalte hain. */
    @Test
    fun `phone wale sawaal browser ko nahi jaate`() {
        assertTrue(parse("battery kitni hai") is EvCommand.Info)
        assertTrue(parse("aaj mausam kaisa hai") is EvCommand.Weather)
        assertTrue(parse("screen pe kya likha hai") is EvCommand.Screen)
        assertTrue(parse("whatsapp kholo") is EvCommand.OpenApp)
    }
}
