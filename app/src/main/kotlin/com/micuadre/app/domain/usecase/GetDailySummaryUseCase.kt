package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Computes the end-of-shift summary for the given session on demand: the trips and intervals come
 * from [ShiftRepository], the amounts spent on the shift from [DeductionRepository]. The amounts are
 * read on every call, so a summary built after they were saved always carries them.
 */
class GetDailySummaryUseCase @Inject constructor(
    private val repository: ShiftRepository,
    private val deductions: DeductionRepository
) {
    suspend operator fun invoke(sessionId: Long): DailySummary =
        repository.summary(sessionId).copy(deductions = amountsOf(sessionId))

    /** Best effort: if the amounts cannot be read, the summary still shows the gross earnings. */
    private suspend fun amountsOf(sessionId: Long): List<SessionDeductionAmount> =
        try {
            deductions.sessionAmounts(sessionId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
}
