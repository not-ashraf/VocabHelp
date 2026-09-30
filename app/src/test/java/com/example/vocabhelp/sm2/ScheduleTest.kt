package com.example.vocabhelp.sm2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleTest {

    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val london = TimeZone.getTimeZone("Europe/London")

    private fun at(tz: TimeZone, y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        Calendar.getInstance(tz).apply {
            clear()
            set(y, mo - 1, d, h, mi, 0)
        }.timeInMillis

    @Test
    fun `word reviewed after the reminder is due at the next day's reminder`() {
        // Reviewed 21:05, interval 1 day, reminder at 21:00 tomorrow.
        val reviewed = at(ist, 2026, 9, 30, 21, 5)
        val due = dueAtStartOfDay(reviewed, intervalDays = 1, timeZone = ist)
        assertEquals(at(ist, 2026, 10, 1, 0, 0), due)
        assertTrue(due <= at(ist, 2026, 10, 1, 21, 0))
    }

    @Test
    fun `interval zero means start of today`() {
        assertEquals(at(ist, 2026, 9, 30, 0, 0), dueAtStartOfDay(at(ist, 2026, 9, 30, 16, 40), 0, ist))
    }

    @Test
    fun `day boundaries survive a DST change`() {
        // UK clocks go back on 25 Oct 2026: that day is 25h long, but due is still midnight.
        val due = dueAtStartOfDay(at(london, 2026, 10, 24, 22, 0), intervalDays = 6, timeZone = london)
        assertEquals(at(london, 2026, 10, 30, 0, 0), due)
    }

    @Test
    fun `next reminder is later today when the time is still ahead`() {
        val now = at(ist, 2026, 9, 30, 17, 32)
        assertEquals((3 * 60 + 28) * 60_000L, millisUntilNext(21, 0, now, ist))
    }

    @Test
    fun `next reminder is tomorrow when the time has passed or is now`() {
        val now = at(ist, 2026, 9, 30, 21, 0)
        assertEquals(24 * 60 * 60_000L, millisUntilNext(21, 0, now, ist))
        val later = at(ist, 2026, 9, 30, 21, 5)
        assertEquals((23 * 60 + 55) * 60_000L, millisUntilNext(21, 0, later, ist))
    }
}
