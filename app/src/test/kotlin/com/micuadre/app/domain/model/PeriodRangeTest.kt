package com.micuadre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PeriodRangeTest {

    private val zone = ZoneId.of("America/Bogota")

    private fun ms(date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    // 2026-10-18 is a Sunday.
    private val now = ms("2026-10-18", "15:30")

    @Test
    fun `today spans local midnight to next local midnight`() {
        val range = PeriodRange.of(Period.TODAY, now, zone)

        assertEquals(LocalDate.parse("2026-10-18"), range.startDate)
        assertEquals(LocalDate.parse("2026-10-18"), range.endDate)
        assertEquals(ms("2026-10-18"), range.fromMs)
        assertEquals(ms("2026-10-19"), range.toMs)
    }

    @Test
    fun `week starts on Monday even when today is Sunday`() {
        val range = PeriodRange.of(Period.WEEK, now, zone)

        assertEquals(LocalDate.parse("2026-10-12"), range.startDate)
        assertEquals(LocalDate.parse("2026-10-18"), range.endDate)
        assertEquals(ms("2026-10-12"), range.fromMs)
        assertEquals(ms("2026-10-19"), range.toMs)
    }

    @Test
    fun `week on a Monday starts that same day`() {
        val range = PeriodRange.of(Period.WEEK, ms("2026-10-12", "09:00"), zone)

        assertEquals(LocalDate.parse("2026-10-12"), range.startDate)
    }

    @Test
    fun `month starts on the first day`() {
        val range = PeriodRange.of(Period.MONTH, now, zone)

        assertEquals(LocalDate.parse("2026-10-01"), range.startDate)
        assertEquals(LocalDate.parse("2026-10-18"), range.endDate)
        assertEquals(ms("2026-10-01"), range.fromMs)
    }

    @Test
    fun `just after local midnight already belongs to the new day`() {
        val range = PeriodRange.of(Period.TODAY, ms("2026-10-19", "00:05"), zone)

        assertEquals(LocalDate.parse("2026-10-19"), range.startDate)
    }
}
