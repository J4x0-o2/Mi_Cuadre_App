package com.micuadre.app.domain.model

/**
 * State machine for the working session (jornada).
 *
 * Transitions:
 *  - Disconnected --(connect)--> Connected
 *  - Connected --(startTrip)--> OnTrip
 *  - OnTrip --(finishTrip)--> Connected
 *  - Connected --(disconnect)--> Disconnected
 *
 * Time references use [android.os.SystemClock.elapsedRealtime] for monotonicity.
 */
sealed interface ShiftState {
    data object Disconnected : ShiftState

    data class Connected(
        val connectedSinceMs: Long
    ) : ShiftState

    data class OnTrip(
        val connectedSinceMs: Long,
        val tripStartedAtMs: Long
    ) : ShiftState
}
