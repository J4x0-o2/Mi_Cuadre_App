package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Recovers the canonical shift state from Room after the OS has killed and
 * recreated the foreground service (`START_STICKY`). The new service
 * instance has a fresh `_currentState = Disconnected`, but the repository
 * (process-wide singleton) still holds the last persisted session row.
 *
 * Without this rehydration the UI keeps showing the controller's stale
 * `Connected` state while the service is in `Disconnected`, and any
 * subsequent intent (e.g. "Nueva carrera") is dropped by the service's
 * guards.
 *
 * Returns the resolved [ShiftState] together with the [Session.id] that
 * backed it, so the service can populate its `activeSessionId` field in
 * the same call. When no active session exists the returned state is
 * [ShiftState.Disconnected] and [Result.sessionId] is `-1L`.
 */
class RehydrateShiftStateUseCase @Inject constructor(
    private val repository: ShiftRepository
) {

    data class Result(
        val state: ShiftState,
        val sessionId: Long
    )

    suspend operator fun invoke(): Result {
        val session = repository.activeSession()
            ?: return Result(ShiftState.Disconnected, sessionId = -1L)

        val inProgress = repository.inProgressTrip(session.id)
        val state: ShiftState = if (inProgress != null) {
            ShiftState.OnTrip(
                connectedSinceMs = session.startedAt,
                tripStartedAtMs = inProgress.startedAt
            )
        } else {
            ShiftState.Connected(connectedSinceMs = session.startedAt)
        }
        return Result(state = state, sessionId = session.id)
    }
}
