package com.micuadre.app.presentation.history

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.domain.model.MonthDetail
import com.micuadre.app.domain.model.YearHistory
import com.micuadre.app.domain.usecase.ObserveMonthHistoryUseCase
import com.micuadre.app.domain.usecase.ObserveYearHistoryUseCase
import com.micuadre.app.presentation.localDays
import com.micuadre.app.presentation.theme.clockChanges
import com.micuadre.app.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * State of the History screens. One instance serves both the year and the month screen (it lives
 * with the activity), and the selected year and month are kept in [SavedStateHandle], so they
 * survive a rotation and a process restart together with the saved screen.
 *
 * Both streams are re-subscribed whenever the local day changes ([switchOnDayChange]) so "en curso",
 * the next-year button and the current week cutoff never go stale while a screen stays open.
 */
@HiltViewModel
class HistoryViewModel internal constructor(
    private val savedState: SavedStateHandle,
    observeYear: (Int, ZoneId) -> Flow<YearHistory>,
    observeMonth: (YearMonth, ZoneId) -> Flow<MonthDetail>,
    clock: Clock,
    zone: () -> ZoneId,
    clockChanges: Flow<Unit>
) : ViewModel() {

    @Inject
    constructor(
        savedState: SavedStateHandle,
        observeYear: ObserveYearHistoryUseCase,
        observeMonth: ObserveMonthHistoryUseCase,
        clock: Clock,
        @ApplicationContext context: Context
    ) : this(savedState, observeYear::invoke, observeMonth::invoke, clock, ZoneId::systemDefault, clockChanges(context))

    private val days = localDays(clock, zone, clockChanges)

    private val selectedYear = savedState.getStateFlow(
        KEY_YEAR,
        Instant.ofEpochMilli(clock.nowMs()).atZone(zone()).year
    )
    private val selectedMonth = savedState.getStateFlow<String?>(KEY_MONTH, null)

    /** The year on screen; null until the first read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val year: StateFlow<YearHistoryModel?> = selectedYear
        .flatMapLatest { year -> days.switchOnDayChange { observeYear(year, it.zone) } }
        .map { YearHistoryModel.from(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** The month on screen; null while none is selected or before the first read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val month: StateFlow<MonthHistoryModel?> = selectedMonth
        .flatMapLatest { text ->
            val selected = text?.let(YearMonth::parse) ?: return@flatMapLatest emptyFlow()
            days.switchOnDayChange { observeMonth(selected, it.zone) }
                .map<MonthDetail, MonthHistoryModel?> { MonthHistoryModel.from(it) }
                // The screen is activity-scoped: drop the previous month at once instead of
                // drawing it until the new selection's first read arrives.
                .onStart { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    fun onPreviousYear() {
        val shown = year.value ?: return
        if (shown.canGoBack) savedState[KEY_YEAR] = shown.year - 1
    }

    fun onNextYear() {
        val shown = year.value ?: return
        if (shown.canGoForward) savedState[KEY_YEAR] = shown.year + 1
    }

    fun onMonthSelected(month: YearMonth) {
        savedState[KEY_MONTH] = month.toString()
    }

    private companion object {
        const val KEY_YEAR = "history_year"
        const val KEY_MONTH = "history_month"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
