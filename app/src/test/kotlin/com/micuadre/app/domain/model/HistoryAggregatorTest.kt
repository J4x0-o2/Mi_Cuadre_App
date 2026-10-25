package com.micuadre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class HistoryAggregatorTest {

    private val zone = ZoneId.of("America/Bogota")

    private fun ms(date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun date(s: String) = LocalDate.parse(s)

    // Sunday 2026-10-18.
    private val now = ms("2026-10-18", "15:30")

    private var nextId = 1L

    private fun trip(sessionId: Long, amount: Long, finished: Boolean = true) = Trip(
        id = nextId++, sessionId = sessionId, platformId = 2L, platformName = "Didi",
        startedAt = 1L, endedAt = if (finished) 2L else null, amountMinor = amount,
        paymentMethod = PaymentMethod.CASH
    )

    /** A session started at [start] with one finished trip of [gross] (none when null). */
    private fun shift(id: Long, start: Long, gross: Long? = null, end: Long? = null) =
        SessionActivity(Session(id, start, end), listOfNotNull(gross?.let { trip(id, it) }), emptyList())

    private fun expense(sessionId: Long, amount: Long) =
        SessionDeductionAmount(sessionId, deductionId = 1L, name = "Fuel", amountMinor = amount)

    private fun year(
        year: Int,
        sessions: List<SessionActivity>,
        amounts: List<SessionDeductionAmount> = emptyList(),
        earliest: Long? = sessions.minOfOrNull { it.session.startedAt },
        nowMs: Long = now
    ) = HistoryAggregator.year(year, sessions, amounts, earliest, nowMs, zone)

    private fun month(
        month: String,
        sessions: List<SessionActivity>,
        amounts: List<SessionDeductionAmount> = emptyList(),
        nowMs: Long = now
    ) = HistoryAggregator.month(YearMonth.parse(month), sessions, amounts, nowMs, zone)

    @Test
    fun `year groups shifts by month newest first and omits months without shifts`() {
        val h = year(
            2026,
            listOf(
                shift(1, ms("2026-02-10", "09:00"), 10_000),
                shift(2, ms("2026-02-20", "09:00"), 5_000),
                shift(3, ms("2026-10-03", "09:00"), 7_000),
                shift(4, ms("2026-07-01", "00:00"), 1_000)
            )
        )

        assertEquals(
            listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 7), YearMonth.of(2026, 2)),
            h.months.map { it.month }
        )
        assertEquals(15_000L, h.months.last().totals.grossMinor)
        assertEquals(2, h.months.last().totals.shiftCount)
    }

    @Test
    fun `year total adds gross expenses net and shift count`() {
        val h = year(
            2026,
            listOf(shift(1, ms("2026-03-05", "09:00"), 20_000), shift(2, ms("2026-04-05", "09:00"), 10_000)),
            listOf(expense(1, 4_000), expense(2, 1_000))
        )

        assertEquals(30_000L, h.totals.grossMinor)
        assertEquals(5_000L, h.totals.expensesMinor)
        assertEquals(25_000L, h.totals.netMinor)
        assertEquals(2, h.totals.shiftCount)
    }

    @Test
    fun `expenses can push the net below zero`() {
        val h = year(2026, listOf(shift(1, ms("2026-03-05", "09:00"), 3_000)), listOf(expense(1, 8_000)))

        assertEquals(-5_000L, h.totals.netMinor)
        assertEquals(-5_000L, h.months.single().totals.netMinor)
    }

    @Test
    fun `a night shift crossing a month boundary belongs to the month it started`() {
        val night = shift(1, ms("2026-08-31", "22:00"), 12_000, end = ms("2026-09-01", "04:00"))

        val h = year(2026, listOf(night), listOf(expense(1, 2_000)))

        assertEquals(listOf(YearMonth.of(2026, 8)), h.months.map { it.month })
        assertEquals(10_000L, h.months.single().totals.netMinor)
    }

    @Test
    fun `a night shift crossing midnight stays in the week of its start day`() {
        val sundayNight = shift(1, ms("2026-09-13", "23:00"), 9_000, end = ms("2026-09-14", "03:00"))

        val m = month("2026-09", listOf(sundayNight))

        val week = m.weeks.single { it.totals.shiftCount > 0 }
        assertEquals(date("2026-09-07"), week.startDate)
        assertEquals(date("2026-09-13"), week.endDate)
    }

    @Test
    fun `open trips never count but the live shift does`() {
        val live = SessionActivity(
            Session(1, ms("2026-10-18", "10:00"), null),
            listOf(trip(1, 6_000), trip(1, 99_000, finished = false)),
            emptyList()
        )

        val h = year(2026, listOf(live))

        assertEquals(6_000L, h.totals.grossMinor)
        assertEquals(1, h.totals.shiftCount)
    }

    @Test
    fun `a shift without trips still counts as a shift`() {
        val h = year(2026, listOf(shift(1, ms("2026-05-02", "09:00"))))

        assertEquals(1, h.months.single().totals.shiftCount)
        assertEquals(0L, h.months.single().totals.grossMinor)
    }

    @Test
    fun `sessions and expenses of other years are ignored`() {
        val h = year(
            2026,
            listOf(shift(1, ms("2025-12-31", "23:00"), 50_000), shift(2, ms("2026-01-01", "00:00"), 1_000)),
            listOf(expense(1, 9_000))
        )

        assertEquals(1_000L, h.totals.grossMinor)
        assertEquals(0L, h.totals.expensesMinor)
        assertEquals(1, h.totals.shiftCount)
    }

    @Test
    fun `current month is flagged only in the current year`() {
        val sessions = listOf(shift(1, ms("2026-10-03", "09:00"), 1_000), shift(2, ms("2026-09-03", "09:00"), 1_000))

        val flags = year(2026, sessions).months.map { it.isCurrent }
        val past = year(2025, listOf(shift(3, ms("2025-10-03", "09:00"), 1_000))).months.map { it.isCurrent }

        assertEquals(listOf(true, false), flags)
        assertEquals(listOf(false), past)
    }

    @Test
    fun `stepper flags the current year and an earlier year with data`() {
        val sessions = listOf(shift(1, ms("2025-06-01", "09:00"), 1_000), shift(2, ms("2026-06-01", "09:00"), 1_000))

        val thisYear = year(2026, sessions)
        val lastYear = year(2025, sessions)

        assertTrue(thisYear.isCurrentYear)
        assertTrue(thisYear.hasPreviousYear)
        assertFalse(lastYear.isCurrentYear)
        assertFalse(lastYear.hasPreviousYear)
    }

    @Test
    fun `the earliest session year is read in the local zone`() {
        // 2025-12-31 23:00 in Bogota is already 2026-01-01 in UTC, but locally it is still 2025.
        val h = year(2026, emptyList(), earliest = ms("2025-12-31", "23:00"))

        assertTrue(h.hasPreviousYear)
    }

    @Test
    fun `empty year has zero totals no months and no previous year`() {
        val h = year(2026, emptyList(), earliest = null)

        assertEquals(HistoryTotals(0L, 0L, 0), h.totals)
        assertEquals(emptyList<MonthHistory>(), h.months)
        assertFalse(h.hasPreviousYear)
    }

    @Test
    fun `month weeks run Monday to Sunday clipped to the month`() {
        // 2026-09-01 is a Tuesday and 2026-09-30 a Wednesday.
        val m = month("2026-09", listOf(shift(1, ms("2026-09-15", "09:00"), 1_000)))

        assertEquals(
            listOf(
                date("2026-09-01") to date("2026-09-06"),
                date("2026-09-07") to date("2026-09-13"),
                date("2026-09-14") to date("2026-09-20"),
                date("2026-09-21") to date("2026-09-27"),
                date("2026-09-28") to date("2026-09-30")
            ),
            m.weeks.map { it.startDate to it.endDate }
        )
    }

    @Test
    fun `month and week totals add up with expenses and shift counts`() {
        val m = month(
            "2026-09",
            listOf(
                shift(1, ms("2026-09-02", "09:00"), 10_000),
                shift(2, ms("2026-09-05", "09:00"), 4_000),
                shift(3, ms("2026-09-29", "09:00"), 6_000)
            ),
            listOf(expense(1, 1_000), expense(3, 2_500))
        )

        assertEquals(20_000L, m.totals.grossMinor)
        assertEquals(3_500L, m.totals.expensesMinor)
        assertEquals(16_500L, m.totals.netMinor)
        assertEquals(3, m.totals.shiftCount)
        assertEquals(HistoryTotals(14_000L, 1_000L, 2), m.weeks.first().totals)
        assertEquals(HistoryTotals(6_000L, 2_500L, 1), m.weeks.last().totals)
        assertEquals(m.totals.grossMinor, m.weeks.sumOf { it.totals.grossMinor })
        assertEquals(m.totals.shiftCount, m.weeks.sumOf { it.totals.shiftCount })
    }

    @Test
    fun `weeks without shifts are listed as zero rows in a past month`() {
        val m = month("2026-09", listOf(shift(1, ms("2026-09-02", "09:00"), 1_000)))

        assertEquals(5, m.weeks.size)
        assertEquals(HistoryTotals(0L, 0L, 0), m.weeks[2].totals)
    }

    @Test
    fun `the current month lists weeks only up to the week of today`() {
        // Today is Sunday 2026-10-18; October starts on a Thursday.
        val m = month("2026-10", listOf(shift(1, ms("2026-10-18", "09:00"), 1_000)))

        assertEquals(
            listOf(
                date("2026-10-01") to date("2026-10-04"),
                date("2026-10-05") to date("2026-10-11"),
                date("2026-10-12") to date("2026-10-18")
            ),
            m.weeks.map { it.startDate to it.endDate }
        )
    }

    @Test
    fun `the week containing today is whole even when today is mid week`() {
        val tuesday = ms("2026-10-13", "10:00")

        val m = month("2026-10", emptyList(), nowMs = tuesday)

        assertEquals(date("2026-10-18"), m.weeks.last().endDate)
    }

    @Test
    fun `month without shifts has zero totals`() {
        val m = month("2026-09", emptyList())

        assertEquals(HistoryTotals(0L, 0L, 0), m.totals)
    }

    @Test
    fun `bar fraction is relative to the max and safe for a non positive max`() {
        assertEquals(0.5f, HistoryAggregator.barFraction(5_000L, 10_000L), 0.0001f)
        assertEquals(1f, HistoryAggregator.barFraction(10_000L, 10_000L), 0.0001f)
        assertEquals(0f, HistoryAggregator.barFraction(-3_000L, 10_000L), 0.0001f)
        assertEquals(0f, HistoryAggregator.barFraction(5_000L, 0L), 0.0001f)
        assertEquals(0f, HistoryAggregator.barFraction(5_000L, -1L), 0.0001f)
    }
}
