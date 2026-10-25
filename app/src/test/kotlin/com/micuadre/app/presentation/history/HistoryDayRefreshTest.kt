package com.micuadre.app.presentation.history

import com.micuadre.app.domain.usecase.ObserveMonthHistoryUseCase
import com.micuadre.app.domain.usecase.ObserveYearHistoryUseCase
import com.micuadre.app.presentation.localDays
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** The history use cases read "today" once per emission, so the screens re-subscribe on a new day. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryDayRefreshTest {

    private val shifts = FakeHistoryShifts(listOf(shiftOn(1, "2026-09-10", 20_000)))

    @Test
    fun `the current month stops being current when the clock crosses a month boundary`() = runTest {
        val clock = FakeClock(msOf("2026-09-30", "23:59:30"))
        val useCase = ObserveYearHistoryUseCase(shifts, FakeHistoryDeductions(), clock)
        val seen = mutableListOf<Boolean>()
        val job = launch {
            localDays(clock, { bogota }).switchOnDayChange { useCase(2026, it.zone) }
                .collect { seen += it.months.single().isCurrent }
        }
        runCurrent()

        clock.now = msOf("2026-10-01", "00:00:01")
        advanceTimeBy(31_000L)
        runCurrent()

        assertEquals(listOf(true, false), seen)
        job.cancel()
    }

    @Test
    fun `the year is no longer the current year after new year`() = runTest {
        val clock = FakeClock(msOf("2026-12-31", "23:59:30"))
        val useCase = ObserveYearHistoryUseCase(shifts, FakeHistoryDeductions(), clock)
        val seen = mutableListOf<Boolean>()
        val job = launch {
            localDays(clock, { bogota }).switchOnDayChange { useCase(2026, it.zone) }
                .collect { seen += it.isCurrentYear }
        }
        runCurrent()

        clock.now = msOf("2027-01-01", "00:00:01")
        advanceTimeBy(31_000L)
        runCurrent()

        assertEquals(listOf(true, false), seen)
        job.cancel()
    }

    @Test
    fun `a month that just started gets its first week when the day changes`() = runTest {
        val clock = FakeClock(msOf("2026-09-30", "23:59:30"))
        val useCase = ObserveMonthHistoryUseCase(shifts, FakeHistoryDeductions(), clock)
        val weekCounts = mutableListOf<Int>()
        val job = launch {
            localDays(clock, { bogota }).switchOnDayChange { useCase(YearMonth.of(2026, 10), it.zone) }
                .collect { weekCounts += it.weeks.size }
        }
        runCurrent()
        assertTrue(weekCounts.single() == 0)

        clock.now = msOf("2026-10-01", "00:00:01")
        advanceTimeBy(31_000L)
        runCurrent()

        assertEquals(listOf(0, 1), weekCounts)
        assertFalse(weekCounts.isEmpty())
        job.cancel()
    }
}
