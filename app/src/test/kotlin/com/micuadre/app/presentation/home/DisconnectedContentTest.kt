package com.micuadre.app.presentation.home

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.HomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisconnectedContentTest {

    @Test
    fun `no open shift and nothing today shows the welcome`() {
        assertEquals(DisconnectedContent.WELCOME, DisconnectedContent.choose(hasOpenShift = false, todaySessions = 0))
    }

    @Test
    fun `no open shift but shifts today shows the day summary`() {
        assertEquals(DisconnectedContent.TODAY, DisconnectedContent.choose(hasOpenShift = false, todaySessions = 2))
    }

    @Test
    fun `a paused shift that started today shows the day summary`() {
        assertEquals(DisconnectedContent.TODAY, DisconnectedContent.choose(hasOpenShift = true, todaySessions = 1))
    }

    @Test
    fun `a paused shift from a previous day shows its own totals`() {
        assertEquals(DisconnectedContent.OPEN_SHIFT, DisconnectedContent.choose(hasOpenShift = true, todaySessions = 0))
    }

    @Test
    fun `while the day summary loads a paused shift keeps the day view and the welcome stays hidden`() {
        assertEquals(DisconnectedContent.TODAY, DisconnectedContent.choose(hasOpenShift = true, todaySessions = null))
        assertEquals(DisconnectedContent.WELCOME, DisconnectedContent.choose(hasOpenShift = false, todaySessions = null))
    }

    @Test
    fun `open shift figures come from its trips and closed intervals`() {
        val breakdown = DayBreakdown(totalMinor = 3_000_000L, cashMinor = 1_000_000L, transferMinor = 2_000_000L, tripCount = 4)
        val t0 = 1_760_000_000_000L
        val intervals = listOf(
            ConnectedInterval(sessionId = 1, startedAt = t0, endedAt = t0 + 3_600_000L),
            ConnectedInterval(sessionId = 1, startedAt = t0 + 7_200_000L, endedAt = t0 + 10_800_000L)
        )

        val model = TodaySummaryModel.ofOpenShift(breakdown, intervals, nowMs = t0 + 20_000_000L)

        assertEquals(3_000_000L, model.totalMinor)
        assertEquals(4, model.tripCount)
        assertEquals(7_200L, model.connectedSeconds)
        assertEquals(1_500_000L, model.perHourMinor)
        assertEquals(1, model.sessionCount)
    }

    @Test
    fun `an open interval still counts up to now`() {
        val t0 = 1_760_000_000_000L
        val breakdown = DayBreakdown(totalMinor = 1_000_000L, cashMinor = 1_000_000L, transferMinor = 0L, tripCount = 1)
        val intervals = listOf(ConnectedInterval(sessionId = 1, startedAt = t0, endedAt = null))

        val model = TodaySummaryModel.ofOpenShift(breakdown, intervals, nowMs = t0 + 7_200_000L)

        assertEquals(7_200L, model.connectedSeconds)
        assertEquals(500_000L, model.perHourMinor)
    }

    @Test
    fun `an open shift under one connected hour has no per hour rate`() {
        val t0 = 1_760_000_000_000L
        val breakdown = DayBreakdown(totalMinor = 1_500_000L, cashMinor = 1_500_000L, transferMinor = 0L, tripCount = 1)
        val intervals = listOf(ConnectedInterval(sessionId = 1, startedAt = t0, endedAt = null))

        val model = TodaySummaryModel.ofOpenShift(breakdown, intervals, nowMs = t0 + 120_000L)

        assertEquals(120L, model.connectedSeconds)
        assertNull(model.perHourMinor)
    }

    // ---- assembly of what the disconnected Home renders ----

    private val t0 = 1_760_000_000_000L

    private fun summary(sessions: Int, total: Long = 0L) =
        PeriodSummary(total, total, 0L, if (total > 0) 1 else 0, 3_600L, sessions, sessions, null, emptyList())

    private fun finished(amount: Long) = Trip(
        id = 1, sessionId = 1, platformId = 2L, platformName = "Didi",
        startedAt = t0, endedAt = t0 + 1, amountMinor = amount, paymentMethod = PaymentMethod.CASH
    )

    private val pausedIntervals = listOf(ConnectedInterval(sessionId = 1, startedAt = t0, endedAt = t0 + 3_600_000L))

    @Test
    fun `paused shift from yesterday renders its own totals and start`() {
        val ui = HomeUiState(
            hasOpenShift = true,
            trips = listOf(finished(40_000L)),
            connectedIntervals = pausedIntervals,
            today = summary(sessions = 0),
            nowMs = t0 + 7_200_000L
        )

        val model = DisconnectedModel.of(ui)

        assertEquals(DisconnectedContent.OPEN_SHIFT, model.content)
        assertEquals(40_000L, model.figures.totalMinor)
        assertEquals(3_600L, model.figures.connectedSeconds)
        assertEquals(t0, model.openShiftStartMs)
    }

    @Test
    fun `paused shift from today renders the today summary`() {
        val ui = HomeUiState(
            hasOpenShift = true,
            trips = listOf(finished(40_000L)),
            connectedIntervals = pausedIntervals,
            today = summary(sessions = 2, total = 90_000L),
            nowMs = t0 + 7_200_000L
        )

        val model = DisconnectedModel.of(ui)

        assertEquals(DisconnectedContent.TODAY, model.content)
        assertEquals(90_000L, model.figures.totalMinor)
        assertEquals(2, model.figures.sessionCount)
    }

    @Test
    fun `no shift and nothing today renders the welcome`() {
        val model = DisconnectedModel.of(HomeUiState(today = summary(sessions = 0)))

        assertEquals(DisconnectedContent.WELCOME, model.content)
    }

    @Test
    fun `no open shift with shifts today renders the today summary`() {
        val model = DisconnectedModel.of(HomeUiState(today = summary(sessions = 1, total = 10_000L)))

        assertEquals(DisconnectedContent.TODAY, model.content)
        assertEquals(10_000L, model.figures.totalMinor)
    }

    @Test
    fun `while today loads a paused shift never flashes the welcome or zero open-shift figures`() {
        val ui = HomeUiState(
            hasOpenShift = true,
            trips = listOf(finished(40_000L)),
            connectedIntervals = pausedIntervals,
            today = null
        )

        val model = DisconnectedModel.of(ui)

        assertEquals(DisconnectedContent.TODAY, model.content)
        assertEquals(TodaySummaryModel.EMPTY, model.figures)
    }
}
