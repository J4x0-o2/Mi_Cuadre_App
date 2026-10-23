package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.util.TimeUtils
import kotlinx.coroutines.flow.first
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
 * the same call.
 *
 * A pause leaves the session open and closes every connected interval, so a session that has
 * intervals but none open is a paused shift: it comes back as [ShiftState.Disconnected] with its
 * session id kept, and is never reconnected on its own. (A session migrated from v1 has a closed
 * backfilled interval and therefore also reads as paused; the driver just taps Connect.) A session with no interval at all was
 * interrupted between being created and opening its first one, so it counts as connected. An open
 * ride always wins (a ride can only exist while connected). When no active session exists the
 * returned state is [ShiftState.Disconnected] and [Result.sessionId] is `-1L`. An active
 * session whose `startedAt` is not a plausible epoch value (legacy data) is
 * closed and treated as no active session.
 */
class RehydrateShiftStateUseCase @Inject constructor(
    private val repository: ShiftRepository
) {

    data class Result(
        val state: ShiftState,
        val sessionId: Long,
        /** Id of the open trip row when [state] is [ShiftState.OnTrip], otherwise null. */
        val tripId: Long? = null
    )

    suspend operator fun invoke(): Result {
        val session = repository.activeSession()
            ?: return Result(ShiftState.Disconnected, sessionId = -1L)

        if (!TimeUtils.isPlausibleEpoch(session.startedAt)) {
            // Legacy row from the old boot-relative build: resuming it would
            // show a ~56-year timer. Close it at its own start (zero length)
            // so it is never offered again, and start from Disconnected.
            repository.endSession(session.id, endedAt = session.startedAt)
            return Result(ShiftState.Disconnected, sessionId = -1L)
        }

        val inProgress = repository.inProgressTrip(session.id)
        val state: ShiftState = when {
            inProgress != null -> ShiftState.OnTrip(
                connectedSinceMs = session.startedAt,
                tripStartedAtMs = inProgress.startedAt
            )
            // Paused: the session stays attached to the result, only the state is Disconnected.
            isPaused(session.id) -> ShiftState.Disconnected
            else -> ShiftState.Connected(connectedSinceMs = session.startedAt)
        }
        return Result(state = state, sessionId = session.id, tripId = inProgress?.id)
    }

    private suspend fun isPaused(sessionId: Long): Boolean {
        val intervals = repository.observeConnectedIntervals(sessionId).first()
        return intervals.isNotEmpty() && intervals.none { it.endedAt == null }
    }
}
