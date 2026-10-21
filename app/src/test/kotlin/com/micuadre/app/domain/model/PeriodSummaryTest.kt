package com.micuadre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class PeriodSummaryTest {

    private val zone = ZoneId.of("America/Bogota")

    private fun ms(date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun date(s: String) = LocalDate.parse(s)

    // Sunday 2026-10-18.
    private val now = ms("2026-10-18", "15:30")

    private var nextId = 1L

    private fun trip(
        sessionId: Long,
        amount: Long?,
        payment: PaymentMethod? = PaymentMethod.CASH,
        finished: Boolean = true
    ) = Trip(
        id = nextId++, sessionId = sessionId, platformId = 2L, platformName = "Didi",
        startedAt = 1L, endedAt = if (finished) 2L else null, amountMinor = amount, paymentMethod = payment
    )

    private fun session(
        id: Long,
        start: Long,
        end: Long? = null,
        trips: List<Trip> = emptyList(),
        intervals: List<ConnectedInterval> = emptyList()
    ) = SessionActivity(Session(id, start, end), trips, intervals)

    private fun interval(sessionId: Long, start: Long, end: Long?) =
        ConnectedInterval(sessionId = sessionId, startedAt = start, endedAt = end)

    private fun summary(period: Period, vararg sessions: SessionActivity) =
        PeriodSummary.of(PeriodRange.of(period, now, zone), sessions.toList(), now, zone)

    @Test
    fun `empty period is all zeros`() {
        val s = summary(Period.TODAY)

        assertEquals(0L, s.totalMinor)
        assertEquals(0L, s.cashMinor)
        assertEquals(0L, s.transferMinor)
        assertEquals(0, s.tripCount)
        assertEquals(0L, s.connectedSeconds)
        assertEquals(0, s.sessionCount)
        assertEquals(0, s.workedDays)
        assertNull(s.firstSessionStartMs)
        assertEquals(emptyList<PeriodBucket>(), s.buckets)
    }

    @Test
    fun `totals split cash and transfer and count only finished trips`() {
        val s = summary(
            Period.TODAY,
            session(
                1, ms("2026-10-18", "08:00"),
                trips = listOf(
                    trip(1, 10_000, PaymentMethod.CASH),
                    trip(1, 5_000, PaymentMethod.TRANSFER),
                    trip(1, null, null, finished = false),
                    trip(1, 7_000, PaymentMethod.CASH, finished = false)
                )
            )
        )

        assertEquals(15_000L, s.totalMinor)
        assertEquals(10_000L, s.cashMinor)
        assertEquals(5_000L, s.transferMinor)
        assertEquals(2, s.tripCount)
    }

    @Test
    fun `night shift counts entirely for the day it started`() {
        val night = session(
            1, ms("2026-10-17", "20:00"), ms("2026-10-18", "02:00"),
            trips = listOf(trip(1, 8_000), trip(1, 9_000))
        )
        val sundayEvening = ms("2026-10-17", "23:00")

        val saturday = PeriodSummary.of(
            PeriodRange.of(Period.TODAY, sundayEvening, zone), listOf(night), sundayEvening, zone
        )

        assertEquals(17_000L, saturday.totalMinor)
        assertEquals(1, saturday.workedDays)
        assertEquals(ms("2026-10-17", "20:00"), saturday.firstSessionStartMs)
        // Seen from the next day it is outside the range, whole.
        assertEquals(0L, summary(Period.TODAY, night).totalMinor)
    }

    @Test
    fun `sessions outside the range are ignored`() {
        val s = summary(
            Period.TODAY,
            session(1, ms("2026-10-17", "23:00"), trips = listOf(trip(1, 5_000))),
            session(2, ms("2026-10-18", "07:00"), trips = listOf(trip(2, 3_000)))
        )

        assertEquals(3_000L, s.totalMinor)
        assertEquals(1, s.sessionCount)
    }

    @Test
    fun `connected seconds sum closed intervals and an open one uses now`() {
        val s = summary(
            Period.TODAY,
            session(
                1, ms("2026-10-18", "08:00"),
                intervals = listOf(
                    interval(1, ms("2026-10-18", "08:00"), ms("2026-10-18", "09:00")),
                    interval(1, ms("2026-10-18", "15:00"), null)
                )
            ),
            session(
                2, ms("2026-10-18", "10:00"),
                intervals = listOf(interval(2, ms("2026-10-18", "10:00"), ms("2026-10-18", "10:10")))
            )
        )

        // 3600 + 1800 (15:00..15:30) + 600
        assertEquals(6_000L, s.connectedSeconds)
        assertEquals(2, s.sessionCount)
        assertEquals(ms("2026-10-18", "08:00"), s.firstSessionStartMs)
    }

    @Test
    fun `worked days counts distinct start dates`() {
        val s = summary(
            Period.WEEK,
            session(1, ms("2026-10-13", "08:00")),
            session(2, ms("2026-10-13", "20:00")),
            session(3, ms("2026-10-15", "08:00"))
        )

        assertEquals(3, s.sessionCount)
        assertEquals(2, s.workedDays)
    }

    @Test
    fun `week has seven day buckets Monday to Sunday with future days at zero`() {
        val wed = ms("2026-10-14", "12:00")
        val s = PeriodSummary.of(
            PeriodRange.of(Period.WEEK, wed, zone),
            listOf(
                session(1, ms("2026-10-12", "08:00"), trips = listOf(trip(1, 4_000))),
                session(2, ms("2026-10-14", "08:00"), trips = listOf(trip(2, 6_000), trip(2, 1_000)))
            ),
            wed, zone
        )

        assertEquals(7, s.buckets.size)
        assertEquals(date("2026-10-12"), s.buckets.first().startDate)
        assertEquals(date("2026-10-18"), s.buckets.last().endDate)
        assertEquals(listOf(4_000L, 0L, 7_000L, 0L, 0L, 0L, 0L), s.buckets.map { it.totalMinor })
        assertEquals(s.buckets.map { it.startDate }, s.buckets.map { it.endDate })
    }

    @Test
    fun `month buckets split at Mondays clipped to the month and today`() {
        val s = summary(
            Period.MONTH,
            session(1, ms("2026-10-02", "08:00"), trips = listOf(trip(1, 1_000))),
            session(2, ms("2026-10-05", "08:00"), trips = listOf(trip(2, 2_000))),
            session(3, ms("2026-10-11", "23:00"), trips = listOf(trip(3, 4_000))),
            session(4, ms("2026-10-18", "08:00"), trips = listOf(trip(4, 8_000)))
        )

        assertEquals(
            listOf(
                Triple(date("2026-10-01"), date("2026-10-04"), 1_000L),
                Triple(date("2026-10-05"), date("2026-10-11"), 6_000L),
                Triple(date("2026-10-12"), date("2026-10-18"), 8_000L)
            ),
            s.buckets.map { Triple(it.startDate, it.endDate, it.totalMinor) }
        )
    }

    @Test
    fun `month buckets are clipped to today when the week is unfinished`() {
        val wed = ms("2026-10-14", "12:00")
        val s = PeriodSummary.of(PeriodRange.of(Period.MONTH, wed, zone), emptyList(), wed, zone)

        assertEquals(date("2026-10-14"), s.buckets.last().endDate)
    }

    @Test
    fun `today has no buckets`() {
        val s = summary(Period.TODAY, session(1, ms("2026-10-18", "08:00"), trips = listOf(trip(1, 1_000))))

        assertEquals(emptyList<PeriodBucket>(), s.buckets)
    }

    // ---- expenses ----

    private fun cost(sessionId: Long, deductionId: Long, name: String, amount: Long) =
        SessionDeductionAmount(sessionId, deductionId, name, amount)

    private fun summaryWithCosts(period: Period, costs: List<SessionDeductionAmount>, vararg sessions: SessionActivity) =
        PeriodSummary.of(PeriodRange.of(period, now, zone), sessions.toList(), now, zone, costs)

    @Test
    fun `without deduction amounts there are no costs`() {
        val s = summary(Period.WEEK, session(1, ms("2026-10-13", "08:00"), trips = listOf(trip(1, 4_000))))

        assertEquals(emptyList<DeductionTotal>(), s.costs)
        assertEquals(0L, s.costsMinor)
        assertEquals(listOf(0L), s.buckets.map { it.costsMinor }.distinct())
    }

    @Test
    fun `costs are totalled per deduction ordered by deduction id and summed`() {
        val s = summaryWithCosts(
            Period.WEEK,
            listOf(
                cost(1, 7, "Fuel", 3_000), cost(1, 9, "Food", 500),
                cost(2, 7, "Fuel", 2_000)
            ),
            session(1, ms("2026-10-13", "08:00")),
            session(2, ms("2026-10-15", "08:00"))
        )

        assertEquals(listOf(DeductionTotal(7, "Fuel", 5_000), DeductionTotal(9, "Food", 500)), s.costs)
        assertEquals(5_500L, s.costsMinor)
    }

    @Test
    fun `a night shift attributes its costs to the day it started`() {
        val night = session(
            1, ms("2026-10-17", "20:00"), ms("2026-10-18", "02:00"),
            trips = listOf(trip(1, 17_000))
        )
        val s = summaryWithCosts(Period.WEEK, listOf(cost(1, 1, "Fuel", 4_000)), night)

        val saturday = s.buckets.single { it.startDate == date("2026-10-17") }
        val sunday = s.buckets.single { it.startDate == date("2026-10-18") }
        assertEquals(4_000L, saturday.costsMinor)
        assertEquals(0L, sunday.costsMinor)
        assertEquals(4_000L, s.costsMinor)
    }

    @Test
    fun `month segments add up the costs of their days`() {
        val s = summaryWithCosts(
            Period.MONTH,
            listOf(cost(1, 1, "Fuel", 1_000), cost(2, 1, "Fuel", 2_000), cost(3, 1, "Fuel", 4_000), cost(4, 2, "Food", 8_000)),
            session(1, ms("2026-10-02", "08:00")),
            session(2, ms("2026-10-05", "08:00")),
            session(3, ms("2026-10-11", "23:00")),
            session(4, ms("2026-10-18", "08:00"))
        )

        assertEquals(listOf(1_000L, 6_000L, 8_000L), s.buckets.map { it.costsMinor })
        assertEquals(15_000L, s.costsMinor)
    }

    @Test
    fun `costs of sessions outside the range or unknown are ignored`() {
        val s = summaryWithCosts(
            Period.TODAY,
            listOf(cost(1, 1, "Fuel", 9_000), cost(2, 1, "Fuel", 3_000), cost(99, 1, "Fuel", 7_000)),
            session(1, ms("2026-10-17", "23:00")),
            session(2, ms("2026-10-18", "07:00"))
        )

        assertEquals(listOf(DeductionTotal(1, "Fuel", 3_000)), s.costs)
        assertEquals(3_000L, s.costsMinor)
    }

    @Test
    fun `a cost without trips still counts and the gross is untouched`() {
        val s = summaryWithCosts(
            Period.TODAY, listOf(cost(1, 1, "Fuel", 3_000)),
            session(1, ms("2026-10-18", "07:00"), trips = listOf(trip(1, 10_000)))
        )

        assertEquals(10_000L, s.totalMinor)
        assertEquals(3_000L, s.costsMinor)
    }
}
