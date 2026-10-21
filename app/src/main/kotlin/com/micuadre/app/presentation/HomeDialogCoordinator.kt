package com.micuadre.app.presentation

import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.expenses.ShiftExpensesState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 *
 * Confirming the end of a shift asks for the shift's expenses first when there are visible
 * deductions: the "Gastos del turno" sheet ([HomeUiState.shiftExpenses]) opens and the shift ends
 * only on save or skip, carrying the amounts. Dismissing the sheet leaves the shift open. Save and
 * skip take the sheet out of the state atomically, so a double tap sends one end action.
 */
class HomeDialogCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
    private val visibleDeductions: suspend () -> List<Deduction>,
    private val endShift: (amounts: Map<Long, Long>) -> Unit,
    private val cancelTrip: (tripStartedAtMs: Long) -> Unit
) {

    fun onEndShiftTapped() {
        ui.update { it.copy(dialog = HomeDialog.EndShift) }
    }

    private var lookupJob: Job? = null

    fun onEndShiftConfirmed() {
        if (!close(HomeDialog.EndShift)) return
        if (lookupJob?.isActive == true) return
        lookupJob = scope.launch {
            val deductions = try {
                visibleDeductions()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The user already confirmed: do not trap them in an open shift over a failed lookup,
                // so the shift ends without asking for expenses.
                emptyList()
            }
            // The shift may have ended another way while the lookup ran: ask about nothing then.
            if (!ui.value.hasOpenShift) return@launch
            if (deductions.isEmpty()) {
                endShift(emptyMap())
            } else {
                ui.update { it.copy(shiftExpenses = ShiftExpensesState.of(deductions)) }
            }
        }
    }

    fun onShiftExpensesEdited(deductionId: Long, raw: String) {
        ui.update { current ->
            val sheet = current.shiftExpenses ?: return@update current
            current.copy(shiftExpenses = sheet.withText(deductionId, raw))
        }
    }

    fun onShiftExpensesSaved() {
        val sheet = takeSheet() ?: return
        endShift(sheet.amounts())
    }

    fun onShiftExpensesSkipped() {
        if (takeSheet() == null) return
        endShift(emptyMap())
    }

    /** Cancels: the shift stays open. */
    fun onShiftExpensesDismissed() {
        takeSheet()
    }

    /** Removes the open sheet and returns it; null when none is open, so only one caller wins. */
    private fun takeSheet(): ShiftExpensesState? =
        ui.getAndUpdate { it.copy(shiftExpenses = null) }.shiftExpenses

    fun onCancelTripTapped() {
        val trip = ui.value.shiftState as? ShiftState.OnTrip ?: return
        ui.update { it.copy(dialog = HomeDialog.CancelTrip(trip.tripStartedAtMs)) }
    }

    fun onCancelTripConfirmed() {
        val dialog = ui.value.dialog as? HomeDialog.CancelTrip ?: return
        if (!close(dialog)) return
        cancelTrip(dialog.tripStartedAtMs)
    }

    /**
     * Must be called every time the shift state is (re)published, with the openness of that same
     * snapshot: drops a cancel dialog whose ride is gone and the expenses sheet of a shift that is
     * no longer open. Openness is passed in because [state] alone cannot tell a paused shift (still
     * open) from an ended one: both are Disconnected.
     */
    fun onShiftState(state: ShiftState, hasOpenShift: Boolean) {
        ui.update {
            val dialog = it.dialog
            val staleDialog = dialog is HomeDialog.CancelTrip &&
                !(state is ShiftState.OnTrip && state.tripStartedAtMs == dialog.tripStartedAtMs)
            val staleSheet = it.shiftExpenses != null && !hasOpenShift
            if (!staleDialog && !staleSheet) return@update it
            it.copy(
                dialog = if (staleDialog) null else dialog,
                shiftExpenses = if (staleSheet) null else it.shiftExpenses
            )
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
