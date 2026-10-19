package com.micuadre.app.domain.model

/**
 * A single trip (carrera).
 *
 * `amountMinor` is stored as centésimos de COP to avoid floating point drift.
 * It is nullable because a trip may be finalized without amount entered yet.
 */
data class Trip(
    val id: Long,
    val sessionId: Long,
    val platformId: Long,
    /** Current name of the platform, also for platforms deleted after the trip. */
    val platformName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val amountMinor: Long?,
    val paymentMethod: PaymentMethod?
) {
    /** Duration in milliseconds. Returns 0 for in-progress trips (endedAt == null). */
    val durationMs: Long get() = ((endedAt ?: startedAt) - startedAt).coerceAtLeast(0L)
}
