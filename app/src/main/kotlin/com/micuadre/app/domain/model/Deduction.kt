package com.micuadre.app.domain.model

/**
 * A user-defined deduction (fuel, app fee, food...) managed from Settings.
 *
 * Deleting is soft ([deletedAt] set): past shifts keep their amounts and the name, while the
 * deduction disappears from Settings and from the end-of-shift sheet. Hidden ([visible] false)
 * deductions stay in Settings but are not asked for when ending a shift.
 * [usualAmountMinor] is an optional prefill for that sheet, in minor units.
 */
data class Deduction(
    val id: Long,
    val name: String,
    val visible: Boolean = true,
    val usualAmountMinor: Long? = null,
    val createdAt: Long = 0L,
    val deletedAt: Long? = null
) {
    val isDeleted: Boolean get() = deletedAt != null
}

/** What one shift spent on a deduction, with the deduction's current name (deleted ones included). */
data class SessionDeductionAmount(
    val sessionId: Long,
    val deductionId: Long,
    val name: String,
    val amountMinor: Long
)
