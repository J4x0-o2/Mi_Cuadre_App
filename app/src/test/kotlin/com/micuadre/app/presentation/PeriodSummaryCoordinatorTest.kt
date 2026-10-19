package com.micuadre.app.presentation

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private fun day(date: String, zone: ZoneId = this.zone) = DayKey(LocalDate.parse(date), zone)

    @Test
    fun `local days emits today and then the next day after midnight`() = runTest {
        val clock = FakeClock(ms("2026-10-18", "23:59:00"))
        val seen = mutableListOf<DayKey>()
        val job = launch { localDays(clock, { zone }).collect { seen.add(it) } }

        runCurrent()
        assertEquals(listOf(day("2026-10-18")), seen)

        clock.now = ms("2026-10-19", "00:00:01")
        advanceTimeBy(61_000L)
        runCurrent()

        assertEquals(listOf(day("2026-10-18"), day("2026-10-19")), seen)
        job.cancel()
    }

    @Test
    fun `local days does not wake before the next midnight`() = runTest {
        val clock = FakeClock(ms("2026-10-18", "12:00:00"))
        val seen = mutableListOf<DayKey>()
        val job = launch { localDays(clock, { zone }).collect { seen.add(it) } }

        advanceTimeBy(11 * 3_600_000L)
        runCurrent()

        assertEquals(1, seen.size)
        job.cancel()
    }

    @Test
    fun `local days re-reads the clock when the wall clock changes`() = runTest {
        val clock = FakeClock(ms("2026-10-18", "12:00:00"))
        val changes = MutableSharedFlow<Unit>()
        val seen = mutableListOf<DayKey>()
        val job = launch { localDays(clock, { zone }, changes).collect { seen.add(it) } }
        runCurrent()

        clock.now = ms("2026-10-20", "09:00:00")
        changes.emit(Unit)
        runCurrent()

        assertEquals(listOf(day("2026-10-18"), day("2026-10-20")), seen)
        job.cancel()
    }

    @Test
    fun `local days re-reads the time zone when it changes`() = runTest {
        var current = zone
        val clock = FakeClock(ms("2026-10-18", "23:30:00")) // 04:30 UTC on the 19th
        val changes = MutableSharedFlow<Unit>()
        val seen = mutableListOf<DayKey>()
        val job = launch { localDays(clock, { current }, changes).collect { seen.add(it) } }
        runCurrent()

        current = ZoneId.of("UTC")
        changes.emit(Unit)
        runCurrent()

        assertEquals(listOf(day("2026-10-18"), day("2026-10-19", ZoneId.of("UTC"))), seen)
        job.cancel()
    }

    @Test
    fun `local days re-arms the midnight sleep after a clock change`() = runTest {
        val clock = FakeClock(ms("2026-10-18", "12:00:00"))
        val changes = MutableSharedFlow<Unit>()
        val seen = mutableListOf<DayKey>()
        val job = launch { localDays(clock, { zone }, changes).collect { seen.add(it) } }
        runCurrent()

        // Clock jumps to 23:59 on the same day: the next wake-up must be one minute away, not 12 h.
        clock.now = ms("2026-10-18", "23:59:00")
        changes.emit(Unit)
        runCurrent()
        clock.now = ms("2026-10-19", "00:00:01")
        advanceTimeBy(61_000L)
        runCurrent()

        assertEquals(day("2026-10-19"), seen.last())
        job.cancel()
    }

    // ---- coordinator ----

    private class Fixture(scope: TestScope, val clock: FakeClock, var zone: ZoneId) {
        val changes = MutableSharedFlow<Unit>()
        val ui = MutableStateFlow(HomeUiState())
        val subscriptions = mutableListOf<Period>()
        val zonesSeen = mutableListOf<ZoneId>()
        val sources = Period.entries.associateWith {
            MutableStateFlow(PeriodSummary(0, 0, 0, 0, 0, 0, 0, null, emptyList()))
        }
        val coordinator = PeriodSummaryCoordinator(
            ui = ui,
            scope = scope.backgroundScope,
            clock = clock,
            zone = { this.zone },
            clockChanges = changes,
            observe = { period: Period, zone: ZoneId ->
                subscriptions.add(period)
                zonesSeen.add(zone)
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
    fun `re-subscribes every period when the clock jumps to another day`() = runTest {
        val f = fixture(ms("2026-10-18", "10:00:00"))
        runCurrent()

        f.clock.now = ms("2026-10-20", "10:00:00")
        f.changes.emit(Unit)
        runCurrent()

        assertEquals(6, f.subscriptions.size)
        assertEquals(LocalDate.of(2026, 10, 20), f.ui.value.todayDate)
    }

    @Test
    fun `a clock change within the same day keeps the subscriptions`() = runTest {
        val f = fixture(ms("2026-10-18", "10:00:00"))
        runCurrent()

        f.clock.now = ms("2026-10-18", "11:00:00")
        f.changes.emit(Unit)
        runCurrent()

        assertEquals(3, f.subscriptions.size)
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

    @Test
    fun `observes every period in the zone the date ticker emitted`() = runTest {
        val f = fixture()
        runCurrent()
        assertEquals(List(3) { zone }, f.zonesSeen)

        val madrid = ZoneId.of("Europe/Madrid")
        f.zone = madrid
        f.changes.emit(Unit)
        runCurrent()

        assertEquals(List(3) { madrid }, f.zonesSeen.takeLast(3))
    }
}
