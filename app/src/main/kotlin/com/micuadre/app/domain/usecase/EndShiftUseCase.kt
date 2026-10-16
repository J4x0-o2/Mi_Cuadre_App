package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Closes the given session, marking its endedAt timestamp.
 */
class EndShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(sessionId: Long, endedAt: Long) {
        repository.endSession(sessionId, endedAt)
    }
}
