package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Records the start of a trip. The actual start row is captured by the
 * service when entering OnTrip; this use case exists for symmetry and to
 * centralize persistence orchestration if/when trip rows are pre-inserted.
 */
class StartTripUseCase @Inject constructor(
    @Suppress("unused") private val repository: ShiftRepository
) {
    @Suppress("UNUSED_PARAMETER")
    suspend operator fun invoke(sessionId: Long, startedAt: Long) {
        // No-op in MVP: trip rows are inserted at finish time (append-only).
        // Kept as a use case for forward compatibility.
    }
}
