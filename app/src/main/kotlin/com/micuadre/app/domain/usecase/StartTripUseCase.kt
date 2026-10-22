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
        // One atomic call: the insert and the read-back cannot be separated by a failure.
        val open = repository.openTrip(sessionId, startedAt)
        // openTrip is idempotent: an already open trip keeps its own start time.
        return Started(tripId = open.id, startedAt = open.startedAt)
    }
}
