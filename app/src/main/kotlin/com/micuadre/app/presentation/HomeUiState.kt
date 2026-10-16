package com.micuadre.app.presentation

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip

/**
 * UI state for the home screen. All fields are stable to support Compose
 * recomposition without surprise allocations.
 */
data class HomeUiState(
    val shiftState: ShiftState = ShiftState.Disconnected,
    val trips: List<Trip> = emptyList(),
    val totalMinor: Long = 0L,
    val elapsedSec: Long = 0L,
    val summary: DailySummary? = null,
    val showFinishSheet: Boolean = false,
    val showPlatformSheet: Boolean = false,
    val needsNotificationPermission: Boolean = false,
    val pendingError: String? = null
)
