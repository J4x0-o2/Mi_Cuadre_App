package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformSelection
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Selection to preselect in the finish-trip sheet: platform and payment method of the most
 * recent finished trip. Falls back to InDriver (also when its "Otra" label is lost) and to
 * cash when there is no previous trip or it has no payment method.
 */
class GetLastUsedPlatformUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(): PlatformSelection {
        val last = repository.lastFinishedTrip() ?: return DEFAULT
        val payment = last.paymentMethod ?: DEFAULT.paymentMethod
        return when (last.platform) {
            Platform.OTHER -> {
                val label = last.platformOtherLabel?.trim()
                if (label.isNullOrEmpty()) DEFAULT.copy(paymentMethod = payment)
                else PlatformSelection(Platform.OTHER, label, payment)
            }
            else -> PlatformSelection(last.platform, paymentMethod = payment)
        }
    }

    private companion object {
        val DEFAULT = PlatformSelection(Platform.INDRIVER, paymentMethod = PaymentMethod.CASH)
    }
}
