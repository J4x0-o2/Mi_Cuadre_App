package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.expenses.ShiftExpensesState
import com.micuadre.app.presentation.finish.FinishSheetState
import java.time.LocalDate

/**
 * UI state for the home screen. All fields are stable to support Compose
 * recomposition without surprise allocations.
 */
data class HomeUiState(
    val shiftState: ShiftState = ShiftState.Disconnected,
    val trips: List<Trip> = emptyList(),
    val totalMinor: Long = 0L,
    /** A session is open, including a paused (Disconnected) one. */
    val hasOpenShift: Boolean = false,
    /** Connected intervals of the open session; pauses are not counted as connected. */
    val connectedIntervals: List<ConnectedInterval> = emptyList(),
    /** Clock reading when the shift fields above were last refreshed; bounds any open interval. */
    val nowMs: Long = 0L,
    /** Seconds of real connected time today. */
    val elapsedSec: Long = 0L,
    /** Seconds since the current trip started (0 when not on a trip). */
    val tripElapsedSec: Long = 0L,
    /** Aggregates of every shift started today / this week / this month; null until loaded. */
    val today: PeriodSummary? = null,
    val week: PeriodSummary? = null,
    val month: PeriodSummary? = null,
    /** Local date [today] refers to; moves at midnight. */
    val todayDate: LocalDate? = null,
    /** Period whose detail sheet is open, or null when it is closed. */
    val periodSheet: Period? = null,
    val summary: DailySummary? = null,
    val finishSheet: FinishSheetState? = null,
    /** Confirmation dialog currently shown over Home, or null. */
    val dialog: HomeDialog? = null,
    /** "Gastos del turno" sheet shown after confirming the end of a shift, or null. */
    val shiftExpenses: ShiftExpensesState? = null,
    val needsNotificationPermission: Boolean = false,
    val pendingMessage: UiMessage? = null
)
