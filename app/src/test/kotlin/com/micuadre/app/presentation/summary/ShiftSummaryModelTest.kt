package com.micuadre.app.presentation.summary

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Trip
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftSummaryModelTest {

    private val utc = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val minute = 60_000L
    // 2026-01-01T08:00:00Z
    private val t0 = 1_767_254_400_000L

    private fun trip(
        platform: Platform,
        amount: Long?,
        payment: PaymentMethod? = PaymentMethod.CASH,
        label: String? = null,
        finished: Boolean = true,
        id: Long = 1L
    ) = Trip(
        id = id, sessionId = 1L, platform = platform, platformOtherLabel = label,
        startedAt = t0, endedAt = if (finished) t0 + 10 * minute else null,
        amountMinor = amount, paymentMethod = payment
    )

    private fun summary(
        trips: List<Trip>,
        startedAt: Long = t0,
        endedAt: Long? = t0 + 2 * hour + 5 * minute + 7_000L
    ) = DailySummary(
        totalAmountMinor = trips.sumOf { it.amountMinor ?: 0L },
        tripCount = trips.size,
        totalDurationMs = 0L,
        avgDurationMs = 0L,
        startedAt = startedAt,
        endedAt = endedAt,
        trips = trips
    )

    @Test
    fun `shift time range uses session start and end`() {
        val model = ShiftSummaryModel.from(summary(emptyList()), utc)

        assertEquals("08:00", model.startTime)
        assertEquals("10:05", model.endTime)
    }

    @Test
    fun `end time falls back to start when the session has no end`() {
        val model = ShiftSummaryModel.from(summary(emptyList(), endedAt = null), utc)

        assertEquals("08:00", model.endTime)
    }

    @Test
    fun `connected duration is the whole shift as h mm ss`() {
        val model = ShiftSummaryModel.from(summary(emptyList()), utc)

        assertEquals("2:05:07", model.connectedText)
    }

    @Test
    fun `connected duration never goes negative`() {
        val model = ShiftSummaryModel.from(summary(emptyList(), endedAt = t0 - hour), utc)

        assertEquals("0:00:00", model.connectedText)
    }

    @Test
    fun `totals split by payment method and only count finished trips`() {
        val trips = listOf(
            trip(Platform.DIDI, 10_000L, PaymentMethod.CASH, id = 1),
            trip(Platform.DIDI, 5_000L, PaymentMethod.TRANSFER, id = 2),
            trip(Platform.INDRIVER, 7_000L, PaymentMethod.CASH, id = 3),
            trip(Platform.INDRIVER, null, null, finished = false, id = 4)
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
            trip(Platform.DIDI, 5_000L, id = 1),
            trip(Platform.INDRIVER, 20_000L, id = 2),
            trip(Platform.INDRIVER, 20_000L, id = 3)
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf(Platform.INDRIVER, Platform.DIDI), bars.map { it.platform })
        assertEquals(40_000L, bars[0].totalMinor)
        assertEquals(2, bars[0].tripCount)
        assertEquals(1f, bars[0].fraction, 0.0001f)
        assertEquals(0.125f, bars[1].fraction, 0.0001f)
    }

    @Test
    fun `platforms without finished trips are hidden`() {
        val trips = listOf(
            trip(Platform.DIDI, 5_000L, id = 1),
            trip(Platform.INDRIVER, null, null, finished = false, id = 2)
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf(Platform.DIDI), bars.map { it.platform })
    }

    @Test
    fun `Otra trips are grouped by their label`() {
        val trips = listOf(
            trip(Platform.OTHER, 3_000L, label = "Uber", id = 1),
            trip(Platform.OTHER, 1_000L, label = " uber ", id = 2),
            trip(Platform.OTHER, 2_000L, label = "Cabify", id = 3)
        )

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(listOf("Uber", "Cabify"), bars.map { it.otherLabel })
        assertEquals(4_000L, bars[0].totalMinor)
    }

    @Test
    fun `no trips yields no bars and zero totals`() {
        val model = ShiftSummaryModel.from(summary(emptyList()), utc)

        assertTrue(model.bars.isEmpty())
        assertEquals(0L, model.totalMinor)
    }

    @Test
    fun `fractions are zero when every platform total is zero`() {
        val trips = listOf(trip(Platform.DIDI, 0L))

        val bars = ShiftSummaryModel.from(summary(trips), utc).bars

        assertEquals(0f, bars.single().fraction, 0.0001f)
    }
}
