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

    // ---- DST ----

    private val santiago = ZoneId.of("America/Santiago")
    private val madrid = ZoneId.of("Europe/Madrid")

    private fun ms(zone: ZoneId, date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private val hourMs = 3_600_000L

    @Test
    fun `spring forward day is 23 hours between local midnights`() {
        // Madrid 2026-03-29: 02:00 -> 03:00.
        val range = PeriodRange.of(Period.TODAY, ms(madrid, "2026-03-29", "12:00"), madrid)

        assertEquals(ms(madrid, "2026-03-29"), range.fromMs)
        assertEquals(ms(madrid, "2026-03-30"), range.toMs)
        assertEquals(23 * hourMs, range.toMs - range.fromMs)
    }

    @Test
    fun `fall back day is 25 hours between local midnights`() {
        // Madrid 2026-10-25: 03:00 -> 02:00.
        val range = PeriodRange.of(Period.TODAY, ms(madrid, "2026-10-25", "12:00"), madrid)

        assertEquals(ms(madrid, "2026-10-25"), range.fromMs)
        assertEquals(ms(madrid, "2026-10-26"), range.toMs)
        assertEquals(25 * hourMs, range.toMs - range.fromMs)
    }

    @Test
    fun `week containing a transition still starts on Monday local midnight`() {
        // Santiago 2026-04-05 (Sunday): 24:00 -> 23:00, clocks go back at midnight.
        val range = PeriodRange.of(Period.WEEK, ms(santiago, "2026-04-05", "12:00"), santiago)

        assertEquals(LocalDate.parse("2026-03-30"), range.startDate)
        assertEquals(LocalDate.parse("2026-04-05"), range.endDate)
        assertEquals(ms(santiago, "2026-03-30"), range.fromMs)
        assertEquals(ms(santiago, "2026-04-06"), range.toMs)
    }

    @Test
    fun `week across the Madrid spring transition is 167 hours but keeps seven day buckets`() {
        val now = ms(madrid, "2026-03-29", "12:00") // Sunday
        val range = PeriodRange.of(Period.WEEK, now, madrid)

        assertEquals(LocalDate.parse("2026-03-23"), range.startDate)
        assertEquals(ms(madrid, "2026-03-23"), range.fromMs)
        assertEquals(167 * hourMs, range.toMs - range.fromMs)

        val summary = PeriodSummary.of(range, emptyList(), now, madrid)
        assertEquals(7, summary.buckets.size)
        assertEquals(
            (0L until 7L).map { range.startDate.plusDays(it) },
            summary.buckets.map { it.startDate }
        )
    }

    @Test
    fun `a session at 00 30 on the fall back day is assigned to that local date`() {
        val now = ms(madrid, "2026-10-25", "12:00")
        val range = PeriodRange.of(Period.WEEK, now, madrid)
        val start = ms(madrid, "2026-10-25", "00:30")
        val activity = SessionActivity(Session(1, start, null), emptyList(), emptyList())

        val summary = PeriodSummary.of(range, listOf(activity), now, madrid)

        assertEquals(1, summary.sessionCount)
        assertEquals(LocalDate.parse("2026-10-25"), summary.buckets.last().startDate)
    }
}
