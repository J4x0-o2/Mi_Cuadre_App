package com.micuadre.app.presentation.home

import com.micuadre.app.domain.model.ConnectedInterval
import org.junit.Assert.assertEquals
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

        val model = TodaySummaryModel.ofOpenShift(breakdown, intervals)

        assertEquals(3_000_000L, model.totalMinor)
        assertEquals(4, model.tripCount)
        assertEquals(7_200L, model.connectedSeconds)
        assertEquals(1_500_000L, model.perHourMinor)
        assertEquals(1, model.sessionCount)
    }
}
