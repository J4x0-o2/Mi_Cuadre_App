package com.micuadre.app.data.local.entity

/** A session's amount joined with the name of its deduction, deleted deductions included. */
data class SessionDeductionRow(
    val sessionId: Long,
    val deductionId: Long,
    val name: String,
    val amountMinor: Long
)
