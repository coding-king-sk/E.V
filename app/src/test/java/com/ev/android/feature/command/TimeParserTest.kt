package com.ev.android.feature.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TimeParser ke tests.
 *
 * Ye JVM tests hain — phone ya emulator ki zaroorat nahi, `gradle test` se
 * seedhe chal jate hain.
 */
class TimeParserTest {

    // ------------------------------------------------------------ duration

    @Test
    fun `digits ke saath minute`() {
        assertEquals(300, TimeParser.durationSeconds("5 minute ka timer"))
    }

    @Test
    fun `hindi ginti ke saath minute`() {
        assertEquals(300, TimeParser.durationSeconds("paanch minute ka timer"))
        assertEquals(600, TimeParser.durationSeconds("das minute ka timer"))
    }

    @Test
    fun `second aur ghanta`() {
        assertEquals(30, TimeParser.durationSeconds("30 second ka timer"))
        assertEquals(7200, TimeParser.durationSeconds("2 ghante ka timer"))
    }

    /** Regression: pehle yahan 7 minute ban jata tha. */
    @Test
    fun `sentence me doosra number ho to bhi sahi number chune`() {
        assertEquals(300, TimeParser.durationSeconds("7 baje wala 5 minute timer"))
    }

    /** Regression: koi bhi random number timer nahi banna chahiye. */
    @Test
    fun `bina unit ke number timer nahi banta`() {
        assertNull(TimeParser.durationSeconds("timer lagao"))
        assertNull(TimeParser.durationSeconds("5 ka timer"))
    }

    @Test
    fun `bahut lamba timer 24 ghante pe cap hota hai`() {
        assertEquals(24 * 3600, TimeParser.durationSeconds("100 ghante ka timer"))
    }

    // --------------------------------------------------------------- clock

    @Test
    fun `subah ka time waisa hi rehta hai`() {
        assertEquals(7 to 0, TimeParser.clockTime("subah 7 baje alarm"))
    }

    @Test
    fun `shaam ka time 24 hour me badalta hai`() {
        assertEquals(19 to 0, TimeParser.clockTime("shaam 7 baje alarm"))
        assertEquals(19 to 30, TimeParser.clockTime("shaam 7:30 alarm"))
    }

    /** Regression: pehle ye dopahar ke 12 baj jate the. */
    @Test
    fun `raat 12 baje matlab aadhi raat`() {
        assertEquals(0 to 0, TimeParser.clockTime("raat 12 baje alarm"))
    }

    @Test
    fun `subah 12 baje bhi aadhi raat`() {
        assertEquals(0 to 0, TimeParser.clockTime("subah 12 baje alarm"))
    }

    @Test
    fun `bina number ke time nahi milta`() {
        assertNull(TimeParser.clockTime("alarm lagao"))
    }

    @Test
    fun `galat time reject hota hai`() {
        assertNull(TimeParser.clockTime("99 baje alarm"))
    }
}
