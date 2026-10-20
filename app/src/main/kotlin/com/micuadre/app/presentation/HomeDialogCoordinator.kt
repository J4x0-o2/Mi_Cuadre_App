package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Confirmation dialogs the Home screen can show; at most one at a time. */
sealed interface HomeDialog {
    data object EndShift : HomeDialog

    /** Bound to the ride it was opened for, so a confirm can never discard a different one. */
    data class CancelTrip(val tripStartedAtMs: Long) : HomeDialog
}

/**
 * Guards destructive Home actions behind a confirmation. The dialog lives in [HomeUiState.dialog];
 * an action runs only when its own confirm arrives while its dialog is open, so a dismiss never
 * changes the shift or the ride.
 */
class HomeDialogCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    private val endShift: () -> Unit,
    private val cancelTrip: (tripStartedAtMs: Long) -> Unit
) {

    fun onEndShiftTapped() {
        ui.update { it.copy(dialog = HomeDialog.EndShift) }
    }

    fun onEndShiftConfirmed() {
        if (!close(HomeDialog.EndShift)) return
        endShift()
    }

    fun onCancelTripTapped() {
        val trip = ui.value.shiftState as? ShiftState.OnTrip ?: return
        ui.update { it.copy(dialog = HomeDialog.CancelTrip(trip.tripStartedAtMs)) }
    }

    fun onCancelTripConfirmed() {
        val dialog = ui.value.dialog as? HomeDialog.CancelTrip ?: return
        if (!close(dialog)) return
        cancelTrip(dialog.tripStartedAtMs)
    }

    /** Must be called every time the shift state is (re)published; drops a cancel dialog whose ride is gone. */
    fun onShiftState(state: ShiftState) {
        ui.update {
            val dialog = it.dialog
            val stale = dialog is HomeDialog.CancelTrip &&
                !(state is ShiftState.OnTrip && state.tripStartedAtMs == dialog.tripStartedAtMs)
            if (stale) it.copy(dialog = null) else it
        }
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
