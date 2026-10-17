package com.micuadre.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    // --- money ---

    @Test
    fun `money formats minor units with es_CO separators`() {
        // 150050 centésimos → $ 1.500,50
        assertEquals("$ 1.500,50", Formatters.money(150_050L))
    }

    @Test
    fun `money formats two decimals with es_CO grouping`() {
        // 150025 centésimos → $ 1.500,25 (no rounding, exact binary form)
        assertEquals("$ 1.500,25", Formatters.money(150_025L))
    }

    @Test
    fun `money no decimals drops the cents part`() {
        assertEquals("$ 1.500", Formatters.moneyNoDecimals(150_000L))
    }

    @Test
    fun `money formats zero`() {
        assertEquals("$ 0,00", Formatters.money(0L))
    }

    // --- parseAmount ---

    @Test
    fun `parseAmount accepts es_CO dot-comma form`() {
        assertEquals(150_050L, Formatters.parseAmount("1.500,50"))
    }

    @Test
    fun `parseAmount accepts US dot form`() {
        assertEquals(150_050L, Formatters.parseAmount("1500.50"))
    }

    @Test
    fun `parseAmount accepts plain integer string`() {
        assertEquals(150_000L, Formatters.parseAmount("1500"))
    }

    @Test
    fun `parseAmount accepts whitespace`() {
        assertEquals(150_000L, Formatters.parseAmount("  1500  "))
    }

    @Test
    fun `parseAmount returns null on garbage`() {
        assertNull(Formatters.parseAmount("abc"))
        assertNull(Formatters.parseAmount(""))
        assertNull(Formatters.parseAmount("--1"))
    }

    @Test
    fun `parseAmount rejects negative values`() {
        assertNull(Formatters.parseAmount("-100"))
    }

    // --- durationHMMSS ---

    @Test
    fun `durationHMMSS formats under one hour`() {
        assertEquals("00:01:30", Formatters.durationHMMSS(90L))
    }

    @Test
    fun `durationHMMSS formats hours`() {
        assertEquals("02:15:45", Formatters.durationHMMSS(2 * 3600L + 15 * 60L + 45L))
    }

    @Test
    fun `durationHMMSS clamps negatives to zero`() {
        assertEquals("00:00:00", Formatters.durationHMMSS(-50L))
    }

    // --- durationMMSS ---

    @Test
    fun `durationMMSS formats under one minute`() {
        assertEquals("00:45", Formatters.durationMMSS(45L))
    }

    @Test
    fun `durationMMSS formats minutes`() {
        assertEquals("12:34", Formatters.durationMMSS(12 * 60L + 34L))
    }

    @Test
    fun `durationMMSS caps at 99 minutes`() {
        // 150 minutes = 9000 seconds; cap is 99 minutes, secs stays at 0
        assertEquals("99:00", Formatters.durationMMSS(150 * 60L))
    }

    @Test
    fun `durationMMSS caps minutes but preserves seconds at boundary`() {
        // 99 min 59 sec = 5999 seconds; under the cap
        assertEquals("99:59", Formatters.durationMMSS(99 * 60L + 59L))
        // 100 min 30 sec = 6030 seconds; minutes saturate at 99, seconds = 30
        assertEquals("99:30", Formatters.durationMMSS(100 * 60L + 30L))
    }

    @Test
    fun `durationMMSS clamps negatives to zero`() {
        assertEquals("00:00", Formatters.durationMMSS(-5L))
    }

    @Test
    fun `durationHMSS drops the hour padding`() {
        assertEquals("0:00:00", Formatters.durationHMSS(0L))
        assertEquals("1:05:09", Formatters.durationHMSS(3_600L + 5 * 60L + 9L))
        assertEquals("12:00:01", Formatters.durationHMSS(12 * 3_600L + 1L))
        assertEquals("0:00:00", Formatters.durationHMSS(-3L))
    }

    @Test
    fun `tripTimer is MM_SS under an hour and H_MM_SS beyond`() {
        assertEquals("00:00", Formatters.tripTimer(0L))
        assertEquals("04:32", Formatters.tripTimer(4 * 60L + 32L))
        assertEquals("59:59", Formatters.tripTimer(3_599L))
        assertEquals("1:00:00", Formatters.tripTimer(3_600L))
        assertEquals("1:02:03", Formatters.tripTimer(3_723L))
    }
}
