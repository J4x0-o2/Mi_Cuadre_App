package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState

/** Requests an external entry point (notification) makes of the UI. */
enum class LaunchRequest { OPEN_FINISH_SHEET }

object LaunchRouting {
    const val ACTION_OPEN_FINISH_TRIP = "com.micuadre.app.action.OPEN_FINISH_TRIP"

    fun requestFor(action: String?): LaunchRequest? =
        if (action == ACTION_OPEN_FINISH_TRIP) LaunchRequest.OPEN_FINISH_SHEET else null
}

/**
 * Holds a [LaunchRequest.OPEN_FINISH_SHEET] until the shift state is known.
 * Consumed exactly once; a request that finds the shift no longer on a trip is dropped.
 */
class PendingFinishRequest {
    enum class Decision { None, OpenSheet }

    private var pending = false

    fun request() {
        pending = true
    }

    fun onState(state: ShiftState): Decision {
        if (!pending) return Decision.None
        return when (state) {
            is ShiftState.OnTrip -> {
                pending = false
                Decision.OpenSheet
            }
            // The trip is already over: the notification was stale.
            is ShiftState.Connected -> {
                pending = false
                Decision.None
            }
            // Cold start: the service may not have restored the state yet.
            is ShiftState.Disconnected -> Decision.None
        }
    }

    fun expire() {
        pending = false
    }
}
