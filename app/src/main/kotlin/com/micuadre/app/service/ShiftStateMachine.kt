package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState

/**
 * Pure state machine for the foreground service.
 *
 * Transitions:
 *  - Disconnected --(Connect)--> Connected(startMs)
 *  - Connected --(StartTrip)--> OnTrip(connectedSinceMs, tripStartedAtMs)
 *  - OnTrip --(FinishTrip)--> Connected(connectedSinceMs)
 *  - Connected --(Disconnect)--> Disconnected
 *  - any --(EndShift)--> Disconnected
 *
 * Invalid transitions are no-ops (return the same state).
 *
 * All time inputs come from [android.os.SystemClock.elapsedRealtime]
 * so values are monotonic and comparable.
 */
class ShiftStateMachine(
    private val nowMs: () -> Long
) {

    sealed interface Action {
        data object Connect : Action
        data class StartTrip(val tripStartedAtMs: Long) : Action
        data object FinishTrip : Action
        data object Disconnect : Action
        data object EndShift : Action
    }

    fun transition(current: ShiftState, action: Action): ShiftState = when (action) {
        Action.Connect -> when (current) {
            is ShiftState.Disconnected -> ShiftState.Connected(connectedSinceMs = nowMs())
            else -> current
        }
        is Action.StartTrip -> when (current) {
            is ShiftState.Connected -> ShiftState.OnTrip(
                connectedSinceMs = current.connectedSinceMs,
                tripStartedAtMs = action.tripStartedAtMs
            )
            else -> current
        }
        Action.FinishTrip -> when (current) {
            is ShiftState.OnTrip -> ShiftState.Connected(connectedSinceMs = current.connectedSinceMs)
            else -> current
        }
        Action.Disconnect -> when (current) {
            is ShiftState.Connected -> ShiftState.Disconnected
            else -> current
        }
        Action.EndShift -> ShiftState.Disconnected
    }
}
