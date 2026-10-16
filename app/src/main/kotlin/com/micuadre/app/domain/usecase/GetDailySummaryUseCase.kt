package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Computes the end-of-shift summary for the given session on demand.
 */
class GetDailySummaryUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(sessionId: Long): DailySummary =
        repository.summary(sessionId)
}
