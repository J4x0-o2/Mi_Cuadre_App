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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Emits the current local date, then again each time midnight passes. Sleeps until the next
 * midnight instead of polling, so it costs one wake-up per day. The wake-up is never shorter
 * than [MIN_SLEEP_MS], which keeps an early timer from spinning.
 */
fun localDates(clock: Clock, zone: ZoneId): Flow<LocalDate> = flow {
    while (true) {
        val now = clock.nowMs()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        emit(today)
        val nextMidnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        delay((nextMidnight - now).coerceAtLeast(MIN_SLEEP_MS))
    }
}

private const val MIN_SLEEP_MS = 1_000L

/**
 * Feeds the today / week / month summaries into [ui] and owns the period detail sheet state.
 *
 * [ObservePeriodSummaryUseCase][com.micuadre.app.domain.usecase.ObservePeriodSummaryUseCase]
 * freezes its range when invoked, so each period is re-subscribed whenever the local date
 * changes (`flatMapLatest` over [localDates]); otherwise "today" would keep showing yesterday
 * after midnight. Within a day the database drives updates, never a timer. JVM-testable; the
 * view model only forwards.
 */
class PeriodSummaryCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    scope: CoroutineScope,
    clock: Clock,
    zone: ZoneId,
    private val observe: (Period) -> Flow<PeriodSummary>
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dates: Flow<LocalDate> = localDates(clock, zone)
        .stateIn(scope, SharingStarted.Eagerly, Instant.ofEpochMilli(clock.nowMs()).atZone(zone).toLocalDate())

    init {
        scope.launch { dates.collect { date -> ui.update { it.copy(todayDate = date) } } }
        Period.entries.forEach { period ->
            scope.launch {
                @OptIn(ExperimentalCoroutinesApi::class)
                dates.flatMapLatest { observe(period) }.collect { summary ->
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
