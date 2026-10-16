package com.micuadre.app.domain.model

/**
 * End-of-shift summary, computed on demand from the persisted trip list.
 */
data class DailySummary(
    val totalAmountMinor: Long,
    val tripCount: Int,
    val totalDurationMs: Long,
    val avgDurationMs: Long
)
