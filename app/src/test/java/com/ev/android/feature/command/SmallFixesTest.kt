package com.ev.android.feature.command

import com.ev.android.feature.daily.DailyTasks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Chhoti galtiyon ke tests.
 *
 * Ye wo cheezein hain jo crash nahi karti thi, par bolne-sunne me galat
 * lagti thi ya kabhi-kabhi shabd chhod deti thi. Ek baar theek karke inhe
 * yahan lock kar diya hai.
 */
class SmallFixesTest {

    @Test
    fun `ek ghanta singular bolta hai`() {
        assertEquals("1 ghanta", DailyTasks.formatDuration(3600))
        assertEquals("2 ghante", DailyTasks.formatDuration(7200))
    }

    @Test
    fun `zero ghante nahi banta`() {
        assertEquals("0 second", DailyTasks.formatDuration(0))
    }

    @Test
    fun `minute aur second wahi rehte hain`() {
        assertEquals("2 minute", DailyTasks.formatDuration(120))
        assertEquals("45 second", DailyTasks.formatDuration(45))
    }

    @Test
    fun `no ko ginti nahi maanta`() {
        assertNull(HinglishWords.number("no"))
        assertEquals(9, HinglishWords.number("nau"))
    }

    @Test
    fun `punctuation ke saath wala shabd bhi pakda jata hai`() {
        assertTrue(HinglishWords.has("awaaz thodi dhime.", HinglishWords.DOWN))
        assertTrue(HinglishWords.has("volume kam karo!", HinglishWords.DOWN))
        assertTrue(HinglishWords.has("battery kitni hai?", HinglishWords.BATTERY))
    }
}
