package com.micuadre.app.presentation.home

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelsTest {

    // 2026-01-01T08:00:00Z
    private val T0 = 1_767_254_400_000L

    private fun trip(
        id: Long = 1,
        platform: Platform = Platform.DIDI,
        label: String? = null,
        start: Long = 0L,
        end: Long? = 600_000L,
        amount: Long? = 100_00L,
        payment: PaymentMethod? = PaymentMethod.CASH
    ) = Trip(id, 1L, platform, label, start, end, amount, payment)

    // ---- day breakdown ----

    @Test
    fun `breakdown splits cash and transfer and counts finished trips`() {
        val result = DayBreakdown.from(
            listOf(
                trip(1, amount = 10_000, payment = PaymentMethod.CASH),
                trip(2, amount = 25_000, payment = PaymentMethod.TRANSFER),
                trip(3, amount = 5_000, payment = PaymentMethod.CASH)
            )
        )

        assertEquals(40_000L, result.totalMinor)
        assertEquals(15_000L, result.cashMinor)
        assertEquals(25_000L, result.transferMinor)
        assertEquals(3, result.tripCount)
    }

    @Test
    fun `breakdown ignores the trip still in progress`() {
        val result = DayBreakdown.from(
            listOf(trip(1, amount = 10_000), trip(2, end = null, amount = null, payment = null))
        )

        assertEquals(1, result.tripCount)
        assertEquals(10_000L, result.totalMinor)
    }

    @Test
    fun `breakdown of nothing is zero`() {
        assertEquals(DayBreakdown(0L, 0L, 0L, 0), DayBreakdown.from(emptyList()))
    }

    @Test
    fun `finished trip without a payment method counts only in the total`() {
        val result = DayBreakdown.from(listOf(trip(amount = 7_000, payment = null)))

        assertEquals(7_000L, result.totalMinor)
        assertEquals(0L, result.cashMinor)
        assertEquals(0L, result.transferMinor)
    }

    // ---- trip rows ----

    @Test
    fun `row detail shows start and end time and rounded-down minutes`() {
        val start = 14L * 3_600_000 + 5L * 60_000
        val row = TripRowModel.from(trip(start = start, end = start + 12 * 60_000 + 30_000), ZoneOffset.UTC)

        assertEquals("14:05 – 14:17 · 12 min", row.detail)
    }

    @Test
    fun `row detail never shows less than one minute`() {
        val row = TripRowModel.from(trip(start = 0L, end = 20_000L), ZoneOffset.UTC)

        assertEquals("00:00 – 00:00 · 1 min", row.detail)
    }

    @Test
    fun `row carries platform label payment and amount`() {
        val row = TripRowModel.from(
            trip(platform = Platform.OTHER, label = "Uber", amount = 9_000, payment = PaymentMethod.TRANSFER),
            ZoneOffset.UTC
        )

        assertEquals(Platform.OTHER, row.platform)
        assertEquals("Uber", row.otherLabel)
        assertEquals(PaymentMethod.TRANSFER, row.payment)
        assertEquals(9_000L, row.amountMinor)
    }

    // ---- badge ----

    @Test
    fun `badge text per platform`() {
        assertEquals("iD", PlatformBadge.text(Platform.INDRIVER, null))
        assertEquals("D", PlatformBadge.text(Platform.DIDI, null))
        assertEquals("U", PlatformBadge.text(Platform.OTHER, " uber "))
        assertEquals("O", PlatformBadge.text(Platform.OTHER, null))
        assertEquals("O", PlatformBadge.text(Platform.OTHER, "  "))
    }

    // ---- elapsed ----

    @Test
    fun `elapsed while connected has no trip time`() {
        val e = Elapsed.of(ShiftState.Connected(connectedSinceMs = 1_000L), nowMs = 61_000L)

        assertEquals(60L, e.shiftSec)
        assertEquals(0L, e.tripSec)
    }

    @Test
    fun `elapsed on trip counts both clocks`() {
        val e = Elapsed.of(ShiftState.OnTrip(connectedSinceMs = 0L, tripStartedAtMs = 100_000L), nowMs = 160_000L)

        assertEquals(160L, e.shiftSec)
        assertEquals(60L, e.tripSec)
    }

    @Test
    fun `elapsed while disconnected is zero and clock skew is clamped`() {
        assertEquals(Elapsed(0L, 0L), Elapsed.of(ShiftState.Disconnected, 5_000L))
        assertEquals(
            Elapsed(0L, 0L),
            Elapsed.of(ShiftState.OnTrip(connectedSinceMs = 9_000L, tripStartedAtMs = 9_000L), nowMs = 1_000L)
        )
    }

    // ---- ring ----

    @Test
    fun `connected seconds come from the intervals so pauses are excluded`() {
        val intervals = listOf(
            ConnectedInterval(sessionId = 1L, startedAt = T0, endedAt = T0 + 600_000L),
            ConnectedInterval(sessionId = 1L, startedAt = T0 + 1_200_000L, endedAt = null)
        )
        val state = ShiftState.Connected(connectedSinceMs = T0 + 1_200_000L)

        val e = Elapsed.of(state, nowMs = T0 + 1_500_000L, intervals = intervals)

        assertEquals(600L + 300L, e.shiftSec)
    }

    @Test
    fun `on a trip the trip seconds are unaffected by the intervals`() {
        val intervals = listOf(ConnectedInterval(sessionId = 1L, startedAt = T0, endedAt = null))
        val state = ShiftState.OnTrip(connectedSinceMs = T0, tripStartedAtMs = T0 + 60_000L)

        val e = Elapsed.of(state, nowMs = T0 + 120_000L, intervals = intervals)

        assertEquals(120L, e.shiftSec)
        assertEquals(60L, e.tripSec)
    }

    @Test
    fun `without intervals the shift seconds fall back to the connected since time`() {
        val e = Elapsed.of(ShiftState.Connected(connectedSinceMs = T0), nowMs = T0 + 30_000L)

        assertEquals(30L, e.shiftSec)
    }

    @Test
    fun `ring shows a visible sliver at the start of a trip`() {
        assertEquals(RingProgress.MIN_FRACTION, RingProgress.fraction(0L), 0.0001f)
    }

    @Test
    fun `ring advances one step per minute`() {
        assertEquals(RingProgress.fraction(60L), RingProgress.fraction(119L), 0.0001f)
        assertTrue(RingProgress.fraction(600L) > RingProgress.fraction(60L))
        assertEquals(10f / 30f, RingProgress.fraction(600L), 0.0001f)
    }

    @Test
    fun `ring caps at a full circle`() {
        assertEquals(1f, RingProgress.fraction(3 * 3_600L), 0f)
    }

    // ---- clock ----

    @Test
    fun `clockTime renders HH_mm in the given zone`() {
        assertEquals("14:05", clockTime(14L * 3_600_000 + 5L * 60_000, ZoneOffset.UTC))
        assertEquals("09:30", clockTime(9L * 3_600_000 + 30L * 60_000 + 59_000, ZoneOffset.UTC))
    }

    // ---- actions ----

    @Test
    fun `disconnect is disabled only while on a trip`() {
        assertFalse(HomeActions.canDisconnect(ShiftState.OnTrip(0L, 1L)))
        assertTrue(HomeActions.canDisconnect(ShiftState.Connected(0L)))
        assertTrue(HomeActions.canDisconnect(ShiftState.Disconnected))
    }

    @Test
    fun `connected offers disconnect then end shift`() {
        assertEquals(
            listOf(HomeAction.DISCONNECT, HomeAction.END_SHIFT),
            HomeActions.secondary(ShiftState.Connected(0L), hasOpenShift = true)
        )
    }

    @Test
    fun `paused shift can only be ended`() {
        assertEquals(
            listOf(HomeAction.END_SHIFT),
            HomeActions.secondary(ShiftState.Disconnected, hasOpenShift = true)
        )
    }

    @Test
    fun `no open shift offers nothing while disconnected`() {
        assertTrue(HomeActions.secondary(ShiftState.Disconnected, hasOpenShift = false).isEmpty())
    }

    @Test
    fun `on trip home offers no secondary actions`() {
        assertTrue(HomeActions.secondary(ShiftState.OnTrip(0L, 1L), hasOpenShift = true).isEmpty())
    }
}
