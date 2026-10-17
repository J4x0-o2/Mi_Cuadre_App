package com.micuadre.app.domain.model

/**
 * End-of-shift summary, computed on demand from the persisted session and trip list.
 *
 * [startedAt] / [endedAt] are the session boundaries (epoch ms); [trips] are the session's
 * trips, from which the presentation derives payment and per-platform breakdowns.
 */
data class DailySummary(
    val totalAmountMinor: Long,
    val tripCount: Int,
    val totalDurationMs: Long,
    val avgDurationMs: Long,
    val startedAt: Long = 0L,
    val endedAt: Long? = null,
    val trips: List<Trip> = emptyList()
)
