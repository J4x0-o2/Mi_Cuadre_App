package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.finish.FinishSheetState

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
    /** Seconds of real connected time today. */
    val elapsedSec: Long = 0L,
    /** Seconds since the current trip started (0 when not on a trip). */
    val tripElapsedSec: Long = 0L,
    val summary: DailySummary? = null,
    val finishSheet: FinishSheetState? = null,
    val needsNotificationPermission: Boolean = false,
    val pendingMessage: UiMessage? = null
)
