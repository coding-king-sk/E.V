package com.ev.android.feature.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Reminder ke time nikalne wale tests.
 *
 * `now` inject karte hain taaki test kabhi bhi chale, result wahi rahe —
 * warna aadhi raat ko CI pe test fail hone lagte.
 */
class ReminderTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Kolkata")

    /** 10 August 2026, 10:00 AM IST */
    private val now: Long = calendar(2026, Calendar.AUGUST, 10, 10, 0)

    private fun calendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = Calendar.getInstance(zone).apply {
        clear()
        set(year, month, day, hour, minute, 0)
    }.timeInMillis

    @Test
    fun `10 minute baad ka reminder abhi se count hota hai`() {
        val at = TimeParser.reminderMillis("10 minute baad yaad dilana", now, zone)
        assertEquals(now + 10 * 60 * 1000L, at)
    }

    @Test
    fun `do ghante baad bhi chalta hai`() {
        val at = TimeParser.reminderMillis("do ghante baad yaad dilana", now, zone)
        assertEquals(now + 2 * 60 * 60 * 1000L, at)
    }

    @Test
    fun `kal subah 8 baje agle din 8 baje hota hai`() {
        val at = TimeParser.reminderMillis("kal subah 8 baje yaad dilana", now, zone)
        assertEquals(calendar(2026, Calendar.AUGUST, 11, 8, 0), at)
    }

    @Test
    fun `parso ka reminder do din baad hota hai`() {
        val at = TimeParser.reminderMillis("parso shaam 6 baje yaad dilana", now, zone)
        assertEquals(calendar(2026, Calendar.AUGUST, 12, 18, 0), at)
    }

    @Test
    fun `aaj ka waqt aage ho to aaj hi lagta hai`() {
        val at = TimeParser.reminderMillis("shaam 7 baje yaad dilana", now, zone)
        assertEquals(calendar(2026, Calendar.AUGUST, 10, 19, 0), at)
    }

    /**
     * Sabse zaroori case: nikla hua waqt.
     *
     * "subah 8 baje" jab 10 baj chuke hain — aaj set kar dete to reminder
     * turant baj jata, jo bilkul bekaar hai.
     */
    @Test
    fun `nikla hua waqt agle din chala jata hai`() {
        val at = TimeParser.reminderMillis("subah 8 baje yaad dilana", now, zone)
        assertEquals(calendar(2026, Calendar.AUGUST, 11, 8, 0), at)
    }

    @Test
    fun `bina time ke reminder nahi banta`() {
        assertNull(TimeParser.reminderMillis("yaad dilana ki dawai leni hai", now, zone))
    }

    // ------------------------------------------------------------- parser

    @Test
    fun `poora reminder command parse hota hai`() {
        val command = CommandParser.parse(
            "kal subah 8 baje yaad dilana ki dawai leni hai",
            emptyList(),
        )

        assertTrue(command is EvCommand.Reminder)
        assertEquals("dawai leni hai", (command as EvCommand.Reminder).text)
    }

    @Test
    fun `ki ke bina bhi body nikal aati hai`() {
        val command = CommandParser.parse("10 minute baad dawai yaad dilana", emptyList())

        assertTrue(command is EvCommand.Reminder)
        assertEquals("dawai", (command as EvCommand.Reminder).text)
    }

    /** Reminder aur alarm alag cheezein hain — aapas me na uljhein. */
    @Test
    fun `alarm command reminder nahi banta`() {
        val command = CommandParser.parse("subah 7 baje alarm lagao", emptyList())
        assertTrue(command is EvCommand.Alarm)
    }

    @Test
    fun `timer command reminder nahi banta`() {
        val command = CommandParser.parse("5 minute ka timer lagao", emptyList())
        assertTrue(command is EvCommand.Timer)
    }
}
