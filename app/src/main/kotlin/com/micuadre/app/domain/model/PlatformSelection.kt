package com.micuadre.app.domain.model

/**
 * What the finish-trip sheet preselects: a platform ([platformId] is null when there is no
 * visible platform to choose) and a payment method (cash unless the last trip said otherwise).
 */
data class PlatformSelection(
    val platformId: Long? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH
)
