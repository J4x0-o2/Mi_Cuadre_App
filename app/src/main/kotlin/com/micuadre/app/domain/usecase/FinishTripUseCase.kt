package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Persists a finished trip.
 *
 * Validation rules (append-only):
 *  - platformId must reference an existing, non-deleted platform (checked by the repository).
 *  - amountMinor must be > 0.
 *  - paymentMethod must be non-null.
 *
 * On validation failure the use case returns [Result.Invalid] and does NOT
 * touch storage. On success it returns [Result.Saved] with the new trip id.
 */
class FinishTripUseCase @Inject constructor(
    private val repository: ShiftRepository
) {

    sealed interface Result {
        data object Saved : Result
        data object Invalid : Result
    }

    suspend operator fun invoke(
        sessionId: Long,
        platformId: Long,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Result {
        if (amountMinor <= 0L) return Result.Invalid
        if (endedAt <= startedAt) return Result.Invalid

        val saved = repository.finishTrip(
            sessionId = sessionId,
            platformId = platformId,
            startedAt = startedAt,
            endedAt = endedAt,
            amountMinor = amountMinor,
            paymentMethod = paymentMethod
        )
        return if (saved) Result.Saved else Result.Invalid
    }
}
