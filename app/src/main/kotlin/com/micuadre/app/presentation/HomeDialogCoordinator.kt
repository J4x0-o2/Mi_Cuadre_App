package com.micuadre.app.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Confirmation dialogs the Home screen can show; at most one at a time. */
sealed interface HomeDialog {
    data object EndShift : HomeDialog
    data object CancelTrip : HomeDialog
}

/**
 * Guards destructive Home actions behind a confirmation. The dialog lives in [HomeUiState.dialog];
 * an action runs only when its own confirm arrives while its dialog is open, so a dismiss never
 * changes the shift or the ride.
 */
class HomeDialogCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    private val endShift: () -> Unit,
    private val cancelTrip: () -> Unit
) {

    fun onEndShiftTapped() {
        ui.update { it.copy(dialog = HomeDialog.EndShift) }
    }

    fun onEndShiftConfirmed() {
        if (!close(HomeDialog.EndShift)) return
        endShift()
    }

    fun onCancelTripTapped() {
        ui.update { it.copy(dialog = HomeDialog.CancelTrip) }
    }

    fun onCancelTripConfirmed() {
        if (!close(HomeDialog.CancelTrip)) return
        cancelTrip()
    }

    fun onDialogDismissed() {
        ui.update { it.copy(dialog = null) }
    }

    /** Closes [dialog] if it is the one showing; false when another (or none) is open. */
    private fun close(dialog: HomeDialog): Boolean {
        if (ui.value.dialog != dialog) return false
        ui.update { it.copy(dialog = null) }
        return true
    }
}
