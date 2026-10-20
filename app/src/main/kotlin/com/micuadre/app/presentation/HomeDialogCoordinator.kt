package com.micuadre.app.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Confirmation dialogs the Home screen can show; at most one at a time. */
sealed interface HomeDialog {
    data object EndShift : HomeDialog
}

/**
 * Guards destructive Home actions behind a confirmation. The dialog lives in [HomeUiState.dialog];
 * the action runs only when the matching confirm arrives, so a dismiss never changes the shift.
 */
class HomeDialogCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    private val endShift: () -> Unit
) {

    fun onEndShiftTapped() {
        ui.update { it.copy(dialog = HomeDialog.EndShift) }
    }

    fun onEndShiftConfirmed() {
        if (ui.value.dialog != HomeDialog.EndShift) return
        ui.update { it.copy(dialog = null) }
        endShift()
    }

    fun onDialogDismissed() {
        ui.update { it.copy(dialog = null) }
    }
}
