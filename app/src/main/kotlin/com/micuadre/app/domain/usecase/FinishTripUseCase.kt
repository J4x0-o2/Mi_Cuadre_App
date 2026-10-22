package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Finishes the open trip [tripId] in place.
 *
 * Validation rules:
 *  - platformId must reference an existing, non-deleted platform (checked by the repository).
 *  - amountMinor must be > 0.
 *  - endedAt must be after the trip start.
 *  - paymentMethod must be non-null.
 *
 * On validation failure the use case returns [Result.Invalid] and the trip stays open. On
 * success it returns [Result.Saved].
 */
class FinishTripUseCase @Inject constructor(
    private val repository: ShiftRepository
) {

    sealed interface Result {
        data object Saved : Result
        data object Invalid : Result
    }

    suspend operator fun invoke(
        tripId: Long,
        platformId: Long,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Result {
        if (amountMinor <= 0L) return Result.Invalid
        if (endedAt <= startedAt) return Result.Invalid

        val saved = repository.finishOpenTrip(
            tripId = tripId,
            platformId = platformId,
            endedAt = endedAt,
            amountMinor = amountMinor,
            paymentMethod = paymentMethod
        )
        return if (saved) Result.Saved else Result.Invalid
    }
}
