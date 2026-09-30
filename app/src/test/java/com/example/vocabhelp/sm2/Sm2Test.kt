package com.example.vocabhelp.sm2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Sm2Test {

    @Test
    fun `first correct answer schedules 1 day`() {
        val next = applySm2(Sm2State.INITIAL, quality =5)
        assertEquals(1, next.repetitions)
        assertEquals(1, next.intervalDays)
        assertTrue("EF should grow on q=5: ${next.ef}", next.ef >= 2.5f)
    }

    @Test
    fun `second correct answer schedules 6 days`() {
        val first = applySm2(Sm2State.INITIAL, quality =5)
        val second = applySm2(first, quality =5)
        assertEquals(2, second.repetitions)
        assertEquals(6, second.intervalDays)
    }

    @Test
    fun `third correct answer multiplies by ease factor`() {
        var s = Sm2State.INITIAL
        repeat(3) { s = applySm2(s, quality =5) }
        assertEquals(3, s.repetitions)
        // After two reps interval is 6, then 6 * EF rounded up.
        assertTrue("interval should be > 6 on third success: ${s.intervalDays}", s.intervalDays > 6)
    }

    @Test
    fun `wrong answer resets reps and interval to 1 day`() {
        var s = Sm2State.INITIAL
        repeat(3) { s = applySm2(s, quality =5) }
        val lapsed = applySm2(s, quality =1)
        assertEquals(0, lapsed.repetitions)
        assertEquals(1, lapsed.intervalDays)
    }

    @Test
    fun `lapse lowers EF so the word comes back sooner in future`() {
        var s = Sm2State.INITIAL
        repeat(3) { s = applySm2(s, quality =5) }
        val lapsed = applySm2(s, quality =1)
        // q=1: 0.1 - 4*(0.08 + 4*0.02) = -0.54
        assertEquals(s.ef - 0.54f, lapsed.ef, 0.001f)
    }

    @Test
    fun `EF never drops below 1_3 on repeated lapses`() {
        var s = Sm2State.INITIAL
        repeat(20) { s = applySm2(s, quality =0) }
        assertTrue("EF floor violated: ${s.ef}", s.ef >= 1.3f)
    }

    @Test
    fun `q=3 grows reps but EF stays roughly flat`() {
        val before = Sm2State.INITIAL
        val after = applySm2(before, quality =3)
        assertEquals(1, after.repetitions)
        assertEquals(1, after.intervalDays)
        // q=3 contribution: 0.1 - 2*(0.08 + 2*0.02) = 0.1 - 0.24 = -0.14, so EF drops a bit.
        assertTrue("EF should drop slightly at q=3: ${after.ef}", after.ef < before.ef)
        assertTrue(after.ef >= 1.3f)
    }
}
