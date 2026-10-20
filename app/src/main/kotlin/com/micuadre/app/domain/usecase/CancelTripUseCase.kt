package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Discards the ride in progress for [sessionId] so it is never counted. Finished trips and the
 * session totals are untouched.
 */
class CancelTripUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(sessionId: Long) {
        repository.cancelInProgressTrip(sessionId)
    }
}
