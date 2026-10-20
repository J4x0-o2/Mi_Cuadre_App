package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ShiftState

enum class PanelStatus { CONNECTED, ON_TRIP }

enum class PanelAction {
    START_TRIP,
    FINISH_TRIP,
    ASK_CANCEL_TRIP,
    CONFIRM_CANCEL_TRIP,
    KEEP_TRIP,
    OPEN_APP
}

/** What the expanded panel shows for a shift state; the screen only renders it. */
data class BubblePanelModel(
    val status: PanelStatus,
    val actions: List<PanelAction>,
    /** The inline "discard this ride?" step is showing. */
    val confirmingCancel: Boolean
) {
    /** The timer shows the current ride rather than the connected time. */
    val showsRideTime: Boolean get() = status == PanelStatus.ON_TRIP

    companion object {
        /** Null while disconnected: there is nothing to act on. */
        fun of(state: ShiftState, confirmingCancel: Boolean): BubblePanelModel? = when (state) {
            is ShiftState.Disconnected -> null
            is ShiftState.Connected -> BubblePanelModel(
                status = PanelStatus.CONNECTED,
                actions = listOf(PanelAction.START_TRIP, PanelAction.OPEN_APP),
                confirmingCancel = false
            )
            is ShiftState.OnTrip -> BubblePanelModel(
                status = PanelStatus.ON_TRIP,
                actions = if (confirmingCancel) {
                    listOf(PanelAction.CONFIRM_CANCEL_TRIP, PanelAction.KEEP_TRIP)
                } else {
                    listOf(PanelAction.FINISH_TRIP, PanelAction.ASK_CANCEL_TRIP, PanelAction.OPEN_APP)
                },
                confirmingCancel = confirmingCancel
            )
        }
    }
}
