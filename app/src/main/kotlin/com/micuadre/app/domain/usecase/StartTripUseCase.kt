package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Persists the ride in progress as an open trip row, so it survives the process being killed.
 *
 * Storage failures propagate: the caller must not enter the on-trip state when this throws,
 * otherwise the ride would exist only in memory.
 */
class StartTripUseCase @Inject constructor(
    private val repository: ShiftRepository
) {

    /** The persisted open trip: [startedAt] is the stored start, which wins over the requested one. */
    data class Started(val tripId: Long, val startedAt: Long)

    suspend operator fun invoke(sessionId: Long, startedAt: Long): Started {
        val tripId = repository.openTrip(sessionId, startedAt)
        // openTrip is idempotent: an already open trip keeps its own start time.
        val open = checkNotNull(repository.inProgressTrip(sessionId)?.takeIf { it.id == tripId }) {
            "open trip $tripId was not found after opening it"
        }
        return Started(tripId = tripId, startedAt = open.startedAt)
    }
}
