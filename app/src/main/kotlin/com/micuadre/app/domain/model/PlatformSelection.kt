package com.micuadre.app.domain.model

/**
 * What the finish-trip sheet preselects: a platform ([otherLabel] is the free-text name when
 * [platform] is [Platform.OTHER]) and a payment method (cash unless the last trip said otherwise).
 */
data class PlatformSelection(
    val platform: Platform,
    val otherLabel: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH
)
