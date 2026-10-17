package com.micuadre.app.service

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.usecase.FinishTripUseCase
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Decision logic for a finish-trip request coming from the UI as raw Intent
 * extras. Parses the payload and runs [FinishTripUseCase], collapsing every
 * failure into [Outcome.Rejected] so the caller keeps the trip open and
 * surfaces an error instead of dropping the request silently.
 */
class FinishTripHandler @Inject constructor(
    private val finishTrip: FinishTripUseCase
) {

    sealed interface Outcome {
        /** Trip persisted; the shift state may advance. */
        data object Finished : Outcome

        /** Payload invalid or persistence failed; the trip must stay open. */
        data object Rejected : Outcome
    }

    suspend fun handle(
        sessionId: Long,
        tripStartedAt: Long,
        endedAt: Long,
        platformName: String?,
        platformOtherLabel: String?,
        amountMinor: Long,
        paymentName: String?
    ): Outcome {
        val platform = platformName?.let { runCatching { Platform.valueOf(it) }.getOrNull() }
        val payment = paymentName?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }
        if (platform == null || payment == null || amountMinor <= 0L) return Outcome.Rejected

        val result = try {
            finishTrip(
                sessionId = sessionId,
                platform = platform,
                platformOtherLabel = platformOtherLabel,
                startedAt = tripStartedAt,
                endedAt = endedAt,
                amountMinor = amountMinor,
                paymentMethod = payment
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            // Persistence failure (disk full, locked DB...): keep the trip open.
            return Outcome.Rejected
        }
        return when (result) {
            FinishTripUseCase.Result.Saved -> Outcome.Finished
            FinishTripUseCase.Result.Invalid -> Outcome.Rejected
        }
    }
}
