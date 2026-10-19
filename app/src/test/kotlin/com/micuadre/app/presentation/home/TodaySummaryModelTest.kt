package com.micuadre.app.presentation.home

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TodaySummaryModelTest {

    private fun summary(
        total: Long = 0L,
        connectedSeconds: Long = 0L,
        sessions: Int = 0,
        trips: Int = 0
    ) = PeriodSummary(
        totalMinor = total,
        cashMinor = total / 2,
        transferMinor = total - total / 2,
        tripCount = trips,
        connectedSeconds = connectedSeconds,
        sessionCount = sessions,
        workedDays = if (sessions > 0) 1 else 0,
        firstSessionStartMs = null,
        buckets = emptyList()
    )

    @Test
    fun `per hour divides the total by connected hours`() {
        val model = TodaySummaryModel.from(summary(total = 3_000_000L, connectedSeconds = 2 * 3_600L, sessions = 1))

        assertEquals(1_500_000L, model.perHourMinor)
    }

    @Test
    fun `per hour rounds to the nearest minor unit`() {
        // 1000 minor over 7 min = 8_571.43 per hour.
        val model = TodaySummaryModel.from(summary(total = 1_000L, connectedSeconds = 7 * 60L, sessions = 1))

        assertEquals(8_571L, model.perHourMinor)
    }

    @Test
    fun `per hour is null under one connected minute`() {
        // The tile shows "0:00" then, so a rate extrapolated from seconds would be nonsense.
        val model = TodaySummaryModel.from(summary(total = 500_000L, connectedSeconds = 59L, sessions = 1))

        assertNull(model.perHourMinor)
    }

    @Test
    fun `per hour is null when there is no connected time`() {
        val model = TodaySummaryModel.from(summary(total = 50_000L, connectedSeconds = 0L, sessions = 1))

        assertNull(model.perHourMinor)
    }

    @Test
    fun `figures are copied from the summary`() {
        val model = TodaySummaryModel.from(summary(total = 10_000L, connectedSeconds = 600L, sessions = 2, trips = 3))

        assertEquals(10_000L, model.totalMinor)
        assertEquals(5_000L, model.cashMinor)
        assertEquals(5_000L, model.transferMinor)
        assertEquals(3, model.tripCount)
        assertEquals(600L, model.connectedSeconds)
        assertEquals(2, model.sessionCount)
    }

    @Test
    fun `has activity only when a shift started today`() {
        assertFalse(TodaySummaryModel.from(summary(sessions = 0)).hasActivity)
        assertTrue(TodaySummaryModel.from(summary(sessions = 1)).hasActivity)
    }

    // ---- subtitle ----

    private val sunday = LocalDate.of(2026, 10, 18)

    @Test
    fun `subtitle shows the capitalised spanish date and the plural shift count`() {
        assertEquals("Domingo 18 de octubre · 2 turnos", TodaySubtitle.format(sunday, 2))
    }

    @Test
    fun `subtitle uses the singular for one shift`() {
        assertEquals("Domingo 18 de octubre · 1 turno", TodaySubtitle.format(sunday, 1))
    }

    @Test
    fun `subtitle is just the date when no shift started today`() {
        assertEquals("Lunes 19 de octubre", TodaySubtitle.format(sunday.plusDays(1), 0))
    }

    // ---- shift start ----

    private fun interval(start: Long, end: Long? = null) = ConnectedInterval(1, 1L, start, end)

    @Test
    fun `shift start is the earliest connected interval`() {
        val state = ShiftState.Connected(connectedSinceMs = 9_000L)
        val intervals = listOf(interval(7_000L, 8_000L), interval(9_000L))

        assertEquals(7_000L, shiftStartMs(state, intervals))
    }

    @Test
    fun `shift start falls back to the current connection when intervals are unknown`() {
        assertEquals(9_000L, shiftStartMs(ShiftState.Connected(connectedSinceMs = 9_000L), emptyList()))
        assertEquals(
            4_000L,
            shiftStartMs(ShiftState.OnTrip(connectedSinceMs = 4_000L, tripStartedAtMs = 5_000L), emptyList())
        )
    }

    @Test
    fun `shift start is null when disconnected without intervals`() {
        assertNull(shiftStartMs(ShiftState.Disconnected, emptyList()))
    }
}
