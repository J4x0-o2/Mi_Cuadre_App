package com.micuadre.app.presentation.summary

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.util.Formatters
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftSummaryModelTest {

    private val utc = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val minute = 60_000L
    // 2026-01-01T08:00:00Z
    private val t0 = 1_767_254_400_000L

    private val indriver = 1L
    private val didi = 2L
    private val uber = 3L
    private val names = mapOf(indriver to "InDriver", didi to "Didi", uber to "Uber")

    private fun trip(
        platformId: Long,
        amount: Long?,
        payment: PaymentMethod? = PaymentMethod.CASH,
        finished: Boolean = true,
        id: Long = 1L
    ) = Trip(
        id = id, sessionId = 1L, platformId = platformId, platformName = names.getValue(platformId),
        startedAt = t0, endedAt = if (finished) t0 + 10 * minute else null,
        amountMinor = amount, paymentMethod = payment
    )

    private fun summary(
        trips: List<Trip>,
        startedAt: Long? = t0,
        endedAt: Long? = t0 + 2 * hour + 5 * minute + 7_000L,
        intervals: List<ConnectedInterval> = emptyList(),
        deductions: List<SessionDeductionAmount> = emptyList()
    ) = DailySummary(
        totalAmountMinor = trips.sumOf { it.amountMinor ?: 0L },
        tripCount = trips.size,
        totalDurationMs = 0L,
        avgDurationMs = 0L,
        startedAt = startedAt,
        endedAt = endedAt,
        trips = trips,
        connectedIntervals = intervals,
        deductions = deductions
    )

    private fun spent(deductionId: Long, name: String, amount: Long) =
        SessionDeductionAmount(sessionId = 1L, deductionId = deductionId, name = name, amountMinor = amount)

    private fun interval(start: Long, end: Long?) =
        ConnectedInterval(sessionId = 1L, startedAt = start, endedAt = end)

    @Test
    fun `range uses session start and end`() {
        val model = ShiftSummaryModel.from(summary(emptyList()), utc, nowMs = t0 + 3 * hour)

        assertEquals(SummaryRange.Today("08:00", "10:05"), model.range)
    }

    @Test
    fun `a session that no longer exists shows no range instead of 1970`() {
        val model = ShiftSummaryModel.from(summary(emptyList(), startedAt = null, endedAt = null), utc, nowMs = t0)

        assertEquals(SummaryRange.Unknown, model.range)
    }

    @Test
    fun `connected time sums the intervals and leaves the pauses out`() {
        val intervals = listOf(
            interval(t0, t0 + hour),
            interval(t0 + 90 * minute, t0 + 2 * hour)
        )

        val model = ShiftSummaryModel.from(summary(emptyList(), intervals = intervals), utc)

        assertEquals("1:30:00", model.connectedText)
    }

    @Test
    fun `a still open interval counts up to the end of the shift`() {
        val intervals = listOf(interval(t0, t0 + hour), interval(t0 + 2 * hour, null))

        val model = ShiftSummaryModel.from(
            summary(emptyList(), endedAt = t0 + 2 * hour + 5 * minute, intervals = intervals),
            utc
        )

        assertEquals("1:05:00", model.connectedText)
    }

    @Test
    fun `a shift without intervals shows zero connected time`() {
        val model = ShiftSummaryModel.from(summary(emptyList()), utc)

        assertEquals("0:00:00", model.connectedText)
    }

    @Test
    fun `totals split by payment method and only count finished trips`() {
        val trips = listOf(
            trip(didi, 10_000L, PaymentMethod.CASH, id = 1),
            trip(didi, 5_000L, PaymentMethod.TRANSFER, id = 2),
            trip(indriver, 7_000L, PaymentMethod.CASH, id = 3),
            trip(indriver, null, null, finished = false, id = 4)
        )

        val model = ShiftSummaryModel.from(summary(trips), utc)

        assertEquals(22_000L, model.totalMinor)
        assertEquals(17_000L, model.cashMinor)
        assertEquals(5_000L, model.transferMinor)
        assertEquals(3, model.tripCount)
    }

    @Test
    fun `bars are relative to the highest platform total and sorted descending`() {
        val trips = listOf(
            trip(didi, 5_000L, id = 1),
            trip(indriver, 20_000L, id = 2),
            trip(indriver, 20_000L, id = 3)
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf(indriver, didi), bars.map { it.platformId })
        assertEquals(40_000L, bars[0].totalMinor)
        assertEquals(2, bars[0].tripCount)
        assertEquals(1f, bars[0].fraction, 0.0001f)
        assertEquals(0.125f, bars[1].fraction, 0.0001f)
    }

    @Test
    fun `platforms without finished trips are hidden`() {
        val trips = listOf(
            trip(didi, 5_000L, id = 1),
            trip(indriver, null, null, finished = false, id = 2)
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf(didi), bars.map { it.platformId })
    }

    @Test
    fun `bars are grouped by platform id and named after the platform`() {
        val trips = listOf(
            trip(uber, 3_000L, id = 1),
            trip(uber, 1_000L, id = 2),
            trip(didi, 2_000L, id = 3)
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf(uber, didi), bars.map { it.platformId })
        assertEquals(listOf("Uber", "Didi"), bars.map { it.name })
        assertEquals(4_000L, bars[0].totalMinor)
    }

    @Test
    fun `two platforms with the same name stay separate bars`() {
        val trips = listOf(
            trip(didi, 3_000L, id = 1),
            Trip(
                id = 2, sessionId = 1L, platformId = 9L, platformName = "Didi",
                startedAt = t0, endedAt = t0 + minute, amountMinor = 1_000L, paymentMethod = PaymentMethod.CASH
            )
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf(didi, 9L), bars.map { it.platformId })
    }

    @Test
    fun `no trips yields no bars and zero totals`() {
        val model = ShiftSummaryModel.from(summary(emptyList()), utc)

        assertTrue(model.bars.isEmpty())
        assertEquals(0L, model.totalMinor)
    }

    @Test
    fun `fractions are zero when every platform total is zero`() {
        val trips = listOf(trip(didi, 0L))

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(0f, bars.single().fraction, 0.0001f)
    }

    @Test
    fun `without deductions the summary stays gross`() {
        val model = ShiftSummaryModel.from(summary(listOf(trip(didi, 10_000L))), utc)

        assertFalse(model.hasDeductions)
        assertTrue(model.deductionLines.isEmpty())
        assertEquals(0L, model.deductionsTotalMinor)
        assertEquals(10_000L, model.totalMinor)
        assertEquals(10_000L, model.netMinor)
    }

    @Test
    fun `deductions list their lines in order with the total and the net`() {
        val deductions = listOf(spent(1L, "Gasolina", 15_000L), spent(2L, "Comida", 3_000L))

        val model = ShiftSummaryModel.from(summary(listOf(trip(didi, 50_000L)), deductions = deductions), utc)

        assertTrue(model.hasDeductions)
        assertEquals(
            listOf("Gasolina" to 15_000L, "Comida" to 3_000L),
            model.deductionLines.map { it.name to it.amountMinor }
        )
        assertEquals(18_000L, model.deductionsTotalMinor)
        assertEquals(50_000L, model.totalMinor)
        assertEquals(32_000L, model.netMinor)
    }

    @Test
    fun `a net below zero is kept negative`() {
        val deductions = listOf(spent(1L, "Gasolina", 30_000L))

        val model = ShiftSummaryModel.from(summary(listOf(trip(didi, 10_000L)), deductions = deductions), utc)

        assertEquals(-20_000L, model.netMinor)
    }

    @Test
    fun `labels show a minus sign for expenses and for a negative net`() {
        val deductions = listOf(spent(1L, "Gasolina", 30_000L))

        val model = ShiftSummaryModel.from(summary(listOf(trip(didi, 10_000L)), deductions = deductions), utc)

        assertEquals("− ${Formatters.moneyNoDecimals(20_000L)}", model.netLabel)
        assertEquals("− ${Formatters.moneyNoDecimals(30_000L)}", model.deductionsTotalLabel)
    }

    @Test
    fun `a positive net has no sign`() {
        val deductions = listOf(spent(1L, "Gasolina", 3_000L))

        val model = ShiftSummaryModel.from(summary(listOf(trip(didi, 10_000L)), deductions = deductions), utc)

        assertEquals(Formatters.moneyNoDecimals(7_000L), model.netLabel)
    }

    @Test
    fun `a deleted deduction keeps the name it was saved with`() {
        // The repository resolves names including soft-deleted deductions; the model shows them as given.
        val deductions = listOf(spent(4L, "Peaje", 2_000L))

        val model = ShiftSummaryModel.from(summary(listOf(trip(didi, 10_000L)), deductions = deductions), utc)

        assertEquals("Peaje", model.deductionLines.single().name)
    }

    @Test
    fun `cash and transfer stay gross when there are deductions`() {
        val trips = listOf(
            trip(didi, 10_000L, PaymentMethod.CASH, id = 1),
            trip(didi, 5_000L, PaymentMethod.TRANSFER, id = 2)
        )

        val model = ShiftSummaryModel.from(summary(trips, deductions = listOf(spent(1L, "Gasolina", 4_000L))), utc)

        assertEquals(10_000L, model.cashMinor)
        assertEquals(5_000L, model.transferMinor)
    }
}
