package com.micuadre.app.presentation

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodSummaryCoordinatorTest {

    private val zone = ZoneId.of("America/Bogota")

    private fun ms(date: String, time: String = "00:00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    private fun summary(total: Long) = PeriodSummary(
        totalMinor = total, cashMinor = total, transferMinor = 0L, tripCount = 1, connectedSeconds = 0L,
        sessionCount = 1, workedDays = 1, firstSessionStartMs = null, buckets = emptyList()
    )

    // ---- date ticker ----

    @Test
    fun `local dates emits today and then the next day after midnight`() = runTest {
        val clock = FakeClock(ms("2026-10-18", "23:59:00"))
        val seen = mutableListOf<LocalDate>()
        val job = launch { localDates(clock, zone).collect { seen.add(it) } }

        runCurrent()
        assertEquals(listOf(LocalDate.of(2026, 10, 18)), seen)

        clock.now = ms("2026-10-19", "00:00:01")
        advanceTimeBy(61_000L)
        runCurrent()

        assertEquals(listOf(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 10, 19)), seen)
        job.cancel()
    }

    @Test
    fun `local dates does not wake before the next midnight`() = runTest {
        val clock = FakeClock(ms("2026-10-18", "12:00:00"))
        val seen = mutableListOf<LocalDate>()
        val job = launch { localDates(clock, zone).collect { seen.add(it) } }

        advanceTimeBy(11 * 3_600_000L)
        runCurrent()

        assertEquals(1, seen.size)
        job.cancel()
    }

    // ---- coordinator ----

    private class Fixture(scope: TestScope, val clock: FakeClock, zone: ZoneId) {
        val ui = MutableStateFlow(HomeUiState())
        val subscriptions = mutableListOf<Period>()
        val sources = Period.entries.associateWith {
            MutableStateFlow(PeriodSummary(0, 0, 0, 0, 0, 0, 0, null, emptyList()))
        }
        val coordinator = PeriodSummaryCoordinator(
            ui = ui,
            scope = scope.backgroundScope,
            clock = clock,
            zone = zone,
            observe = { period: Period ->
                subscriptions.add(period)
                sources.getValue(period) as Flow<PeriodSummary>
            }
        )
    }

    private fun TestScope.fixture(now: Long = ms("2026-10-18", "10:00:00")) =
        Fixture(this, FakeClock(now), zone)

    @Test
    fun `publishes today week and month summaries and the current date`() = runTest {
        val f = fixture()
        runCurrent()

        f.sources.getValue(Period.TODAY).value = summary(100L)
        f.sources.getValue(Period.WEEK).value = summary(700L)
        f.sources.getValue(Period.MONTH).value = summary(3_000L)
        runCurrent()

        assertEquals(100L, f.ui.value.today?.totalMinor)
        assertEquals(700L, f.ui.value.week?.totalMinor)
        assertEquals(3_000L, f.ui.value.month?.totalMinor)
        assertEquals(LocalDate.of(2026, 10, 18), f.ui.value.todayDate)
    }

    @Test
    fun `summaries are null until the first emission`() = runTest {
        val f = fixture()

        assertNull(f.ui.value.today)
        assertNull(f.ui.value.week)
        assertNull(f.ui.value.month)
    }

    @Test
    fun `re-subscribes every period when the local date changes`() = runTest {
        val f = fixture(ms("2026-10-18", "23:59:00"))
        runCurrent()
        assertEquals(3, f.subscriptions.size)

        f.clock.now = ms("2026-10-19", "00:00:01")
        advanceTimeBy(61_000L)
        runCurrent()

        assertEquals(6, f.subscriptions.size)
        assertEquals(LocalDate.of(2026, 10, 19), f.ui.value.todayDate)
    }

    @Test
    fun `does not re-subscribe while the date stays the same`() = runTest {
        val f = fixture(ms("2026-10-18", "08:00:00"))
        runCurrent()

        advanceTimeBy(3_600_000L)
        runCurrent()

        assertEquals(3, f.subscriptions.size)
    }

    @Test
    fun `tapping a period opens its sheet and dismissing closes it`() = runTest {
        val f = fixture()

        f.coordinator.onPeriodTapped(Period.WEEK)
        assertEquals(Period.WEEK, f.ui.value.periodSheet)

        f.coordinator.onPeriodTapped(Period.MONTH)
        assertEquals(Period.MONTH, f.ui.value.periodSheet)

        f.coordinator.onPeriodSheetDismissed()
        assertNull(f.ui.value.periodSheet)
    }
}
