package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.PlatformSelection
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Selection to preselect in the finish-trip sheet: platform and payment method of the most
 * recent finished trip. The platform falls back to the first visible one when the last used
 * platform is hidden or deleted (null when none is visible); the payment method falls back to
 * cash when there is no previous trip or it has no payment method.
 */
class GetLastUsedPlatformUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(): PlatformSelection {
        val visible = repository.observeVisiblePlatforms().first()
        val last = repository.lastFinishedTrip()
        val platformId = last?.platformId?.takeIf { id -> visible.any { it.id == id } }
            ?: visible.firstOrNull()?.id
        return PlatformSelection(
            platformId = platformId,
            paymentMethod = last?.paymentMethod ?: PaymentMethod.CASH
        )
    }
}
