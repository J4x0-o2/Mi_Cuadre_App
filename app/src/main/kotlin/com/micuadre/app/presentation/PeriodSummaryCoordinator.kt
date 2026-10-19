package com.micuadre.app.presentation

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.util.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The local [date] seen in [zone]; two keys differ when either the day or the zone moved. */
data class DayKey(val date: LocalDate, val zone: ZoneId)

/**
 * Emits the current local day, then again each time midnight passes. Sleeps until the next
 * midnight instead of polling, so it costs one wake-up per day. The wake-up is never shorter
 * than [MIN_SLEEP_MS], which keeps an early timer from spinning. Each [clockChanges] emission
 * (time zone or wall-clock change) re-reads the clock and [zone] and re-arms the sleep, so a
 * jump is picked up at once instead of at the previously computed midnight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun localDays(
    clock: Clock,
    zone: () -> ZoneId,
    clockChanges: Flow<Unit> = emptyFlow()
): Flow<DayKey> = clockChanges.onStart { emit(Unit) }.flatMapLatest {
    flow {
        while (true) {
            val now = clock.nowMs()
            val zoneNow = zone()
            val today = Instant.ofEpochMilli(now).atZone(zoneNow).toLocalDate()
            emit(DayKey(today, zoneNow))
            val nextMidnight = today.plusDays(1).atStartOfDay(zoneNow).toInstant().toEpochMilli()
            delay((nextMidnight - now).coerceAtLeast(MIN_SLEEP_MS))
        }
    }
}

private const val MIN_SLEEP_MS = 1_000L

private fun initialDay(clock: Clock, zone: () -> ZoneId): DayKey {
    val zoneNow = zone()
    return DayKey(Instant.ofEpochMilli(clock.nowMs()).atZone(zoneNow).toLocalDate(), zoneNow)
}

/**
 * Feeds the today / week / month summaries into [ui] and owns the period detail sheet state.
 *
 * [ObservePeriodSummaryUseCase][com.micuadre.app.domain.usecase.ObservePeriodSummaryUseCase]
 * freezes its range when invoked, so each period is re-subscribed whenever the local day
 * (date or time zone) changes (`flatMapLatest` over [localDays]); otherwise "today" would keep showing yesterday
 * after midnight. Within a day the database drives updates, never a timer. JVM-testable; the
 * view model only forwards.
 */
class PeriodSummaryCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    scope: CoroutineScope,
    clock: Clock,
    zone: () -> ZoneId,
    clockChanges: Flow<Unit> = emptyFlow(),
    private val observe: (Period) -> Flow<PeriodSummary>
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val days: Flow<DayKey> = localDays(clock, zone, clockChanges)
        .stateIn(scope, SharingStarted.Eagerly, initialDay(clock, zone))

    init {
        scope.launch { days.collect { day -> ui.update { it.copy(todayDate = day.date) } } }
        Period.entries.forEach { period ->
            scope.launch {
                @OptIn(ExperimentalCoroutinesApi::class)
                days.flatMapLatest { observe(period) }.collect { summary ->
                    ui.update {
                        when (period) {
                            Period.TODAY -> it.copy(today = summary)
                            Period.WEEK -> it.copy(week = summary)
                            Period.MONTH -> it.copy(month = summary)
                        }
                    }
                }
            }
        }
    }

    fun onPeriodTapped(period: Period) {
        ui.update { it.copy(periodSheet = period) }
    }

    fun onPeriodSheetDismissed() {
        ui.update { it.copy(periodSheet = null) }
    }
}
