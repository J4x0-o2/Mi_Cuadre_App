package com.micuadre.app.presentation.theme

import com.micuadre.app.domain.model.ThemeMode
import com.micuadre.app.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStateTest {

    private val zone = ZoneId.of("America/Bogota")
    private val hour = 3_600_000L

    private fun ms(h: Int, min: Int = 0) =
        ZonedDateTime.of(LocalDateTime.of(2026, 10, 17, h, min), zone).toInstant().toEpochMilli()

    /** Wall clock = [startMs] + virtual time, so delays in the test advance "now". */
    private fun TestScope.virtualClock(startMs: Long) = object : Clock {
        override fun nowMs(): Long = startMs + currentTime
    }

    @Test
    fun nonScheduledModeEmitsOnceAndNeverTicks() = runTest {
        val seen = mutableListOf<ThemeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            themeStates(MutableStateFlow(ThemeMode.DARK), virtualClock(ms(10)), { zone }).toList(seen)
        }
        advanceTimeBy(48 * hour)
        job.cancel()
        assertEquals(listOf(ThemeUiState(ThemeMode.DARK, ms(10))), seen)
    }

    @Test
    fun scheduledModeReEmitsAtEachBoundary() = runTest {
        val seen = mutableListOf<ThemeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            themeStates(MutableStateFlow(ThemeMode.SCHEDULED), virtualClock(ms(17, 30)), { zone }).toList(seen)
        }
        advanceTimeBy(30 * 60_000L)
        assertEquals(listOf(ms(17, 30)), seen.map { it.nowMs })
        advanceTimeBy(1L) // crosses 18:00
        assertEquals(listOf(ms(17, 30), ms(18)), seen.map { it.nowMs })
        advanceTimeBy(12 * hour) // crosses 06:00
        assertEquals(listOf(ms(17, 30), ms(18), ms(18) + 12 * hour), seen.map { it.nowMs })
        job.cancel()
    }

    @Test
    fun changingModeRestartsTheTicker() = runTest {
        val mode = MutableStateFlow(ThemeMode.SCHEDULED)
        val seen = mutableListOf<ThemeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            themeStates(mode, virtualClock(ms(10)), { zone }).toList(seen)
        }
        advanceTimeBy(hour)
        mode.value = ThemeMode.LIGHT
        advanceTimeBy(48 * hour)
        job.cancel()
        assertEquals(
            listOf(ThemeUiState(ThemeMode.SCHEDULED, ms(10)), ThemeUiState(ThemeMode.LIGHT, ms(11))),
            seen
        )
    }

    @Test
    fun clockChangeReArmsTheScheduleFromAFreshReading() = runTest {
        val zoneNow = zone
        var nowMs = ms(10)
        val clock = object : Clock { override fun nowMs() = nowMs }
        val changes = MutableSharedFlow<Unit>()
        val seen = mutableListOf<ThemeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            themeStates(MutableStateFlow(ThemeMode.SCHEDULED), clock, { zoneNow }, changes).toList(seen)
        }
        assertEquals(listOf(ms(10)), seen.map { it.nowMs })
        // The user moves the clock to 17:59:59.999 and the next switch must be 1 ms away.
        nowMs = ms(18) - 1
        changes.emit(Unit)
        assertEquals(listOf(ms(10), ms(18) - 1), seen.map { it.nowMs })
        nowMs = ms(18)
        advanceTimeBy(2L)
        assertEquals(listOf(ms(10), ms(18) - 1, ms(18)), seen.map { it.nowMs })
        job.cancel()
    }

    @Test
    fun clockChangeDoesNotDuplicateTheTickerOfAnOldArming() = runTest {
        val nowMs = ms(10)
        val clock = object : Clock { override fun nowMs() = nowMs + currentTime }
        val changes = MutableSharedFlow<Unit>()
        val seen = mutableListOf<ThemeUiState>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            themeStates(MutableStateFlow(ThemeMode.SCHEDULED), clock, { zone }, changes).toList(seen)
        }
        changes.emit(Unit)
        changes.emit(Unit)
        advanceTimeBy(8 * hour + 1) // crosses 18:00 once
        job.cancel()
        assertEquals(1, seen.count { it.nowMs >= ms(18) })
    }
}
