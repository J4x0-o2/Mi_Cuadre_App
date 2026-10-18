package com.micuadre.app.presentation.summary

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryRangeTest {

    private val utc = ZoneOffset.UTC

    private fun ms(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = utc): Long =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun `same day as now keeps Hoy with the clock times`() {
        val range = SummaryRange.of(ms(17, 8), ms(17, 10, 5), nowMs = ms(17, 10, 6), zone = utc)

        assertEquals(SummaryRange.Today("08:00", "10:05"), range)
    }

    @Test
    fun `a shift that crossed midnight shows both dates`() {
        val range = SummaryRange.of(ms(17, 22), ms(18, 2), nowMs = ms(18, 2, 1), zone = utc)

        assertEquals(SummaryRange.Span("17 oct", "22:00", "18 oct", "02:00"), range)
    }

    @Test
    fun `a same day shift viewed on another day shows its date`() {
        val range = SummaryRange.of(ms(17, 8), ms(17, 10), nowMs = ms(19, 9), zone = utc)

        assertEquals(SummaryRange.SameDay("17 oct", "08:00", "10:00"), range)
    }

    @Test
    fun `day boundaries follow the given zone not UTC`() {
        val bogota = ZoneId.of("America/Bogota")
        // 22:00 -> 23:30 Bogota is 03:00 -> 04:30 UTC of the next day: still one local day.
        val range = SummaryRange.of(
            ms(17, 22, zone = bogota),
            ms(17, 23, 30, zone = bogota),
            nowMs = ms(17, 23, 40, zone = bogota),
            zone = bogota
        )

        assertEquals(SummaryRange.Today("22:00", "23:30"), range)
    }

    @Test
    fun `an open session ends now`() {
        val range = SummaryRange.of(ms(17, 8), endMs = null, nowMs = ms(17, 9), zone = utc)

        assertEquals(SummaryRange.Today("08:00", "09:00"), range)
    }

    @Test
    fun `an end before the start is clamped to the start`() {
        val range = SummaryRange.of(ms(17, 8), ms(17, 7), nowMs = ms(17, 9), zone = utc)

        assertEquals(SummaryRange.Today("08:00", "08:00"), range)
    }

    @Test
    fun `missing or implausible start is unknown`() {
        assertEquals(SummaryRange.Unknown, SummaryRange.of(null, ms(17, 8), nowMs = ms(17, 9), zone = utc))
        assertEquals(SummaryRange.Unknown, SummaryRange.of(0L, ms(17, 8), nowMs = ms(17, 9), zone = utc))
        assertEquals(SummaryRange.Unknown, SummaryRange.of(5_000L, null, nowMs = ms(17, 9), zone = utc))
    }

    @Test
    fun `month abbreviations are Spanish without a trailing dot`() {
        fun startDate(month: Int): String {
            val start = ZonedDateTime.of(2026, month, 3, 22, 0, 0, 0, utc).toInstant().toEpochMilli()
            val end = ZonedDateTime.of(2026, month, 4, 1, 0, 0, 0, utc).toInstant().toEpochMilli()
            return (SummaryRange.of(start, end, nowMs = end, zone = utc) as SummaryRange.Span).startDate
        }

        assertEquals("3 ene", startDate(1))
        assertEquals("3 oct", startDate(10))
        assertEquals("3 dic", startDate(12))
    }
}
