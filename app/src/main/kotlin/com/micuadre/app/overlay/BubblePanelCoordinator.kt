package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Side effects the panel can trigger; implemented by the overlay glue. */
interface BubbleCommands {
    fun startTrip()
    fun cancelTrip(tripStartedAtMs: Long)

    /** Opens the app straight into the finish sheet (same entry as the notification action). */
    fun openFinishTrip()
    fun openApp()
}

data class BubbleUiState(
    val shiftState: ShiftState = ShiftState.Disconnected,
    val expanded: Boolean = false,
    /** Start of the ride whose cancel is awaiting confirmation, or null. */
    val confirmingCancelFor: Long? = null
) {
    val panel: BubblePanelModel?
        get() = BubblePanelModel.of(
            state = shiftState,
            confirmingCancel = confirmingCancelFor != null &&
                (shiftState as? ShiftState.OnTrip)?.tripStartedAtMs == confirmingCancelFor
        )
}

/**
 * Expanded/collapsed state of the bubble and the actions behind its panel. An action runs only
 * when the current shift state allows it, and a cancel confirmation is bound to the ride it was
 * asked for, so a stale tap never touches a different ride.
 */
class BubblePanelCoordinator(private val commands: BubbleCommands) {

    private val _ui = MutableStateFlow(BubbleUiState())
    val ui: StateFlow<BubbleUiState> = _ui.asStateFlow()

    fun onShiftState(state: ShiftState) {
        _ui.update { current ->
            val sameRide = state is ShiftState.OnTrip && state.tripStartedAtMs == current.confirmingCancelFor
            current.copy(
                shiftState = state,
                expanded = current.expanded && state !is ShiftState.Disconnected,
                confirmingCancelFor = current.confirmingCancelFor.takeIf { sameRide }
            )
        }
    }

    fun onBubbleTapped() {
        _ui.update { if (it.shiftState is ShiftState.Disconnected) it else it.copy(expanded = true) }
    }

    fun onCollapse() {
        _ui.update { it.copy(expanded = false, confirmingCancelFor = null) }
    }

    fun onAction(action: PanelAction) {
        val state = _ui.value.shiftState
        when (action) {
            PanelAction.START_TRIP -> if (state is ShiftState.Connected) {
                commands.startTrip()
                onCollapse()
            }
            PanelAction.FINISH_TRIP -> if (state is ShiftState.OnTrip) {
                commands.openFinishTrip()
                onCollapse()
            }
            PanelAction.OPEN_APP -> if (state !is ShiftState.Disconnected) {
                commands.openApp()
                onCollapse()
            }
            PanelAction.ASK_CANCEL_TRIP -> if (state is ShiftState.OnTrip) {
                _ui.update { it.copy(confirmingCancelFor = state.tripStartedAtMs) }
            }
            PanelAction.KEEP_TRIP -> _ui.update { it.copy(confirmingCancelFor = null) }
            PanelAction.CONFIRM_CANCEL_TRIP -> {
                val confirming = _ui.value.confirmingCancelFor
                if (state is ShiftState.OnTrip && confirming == state.tripStartedAtMs) {
                    commands.cancelTrip(confirming)
                    onCollapse()
                }
            }
        }
    }
}
