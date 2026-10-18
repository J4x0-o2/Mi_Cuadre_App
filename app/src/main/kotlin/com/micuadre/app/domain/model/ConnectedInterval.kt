package com.micuadre.app.domain.model

/**
 * A stretch of real connected time within a session. Pauses (disconnected time) are the gaps
 * between intervals. [endedAt] is null while the interval is still open.
 */
data class ConnectedInterval(
    val id: Long = 0L,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long?
)
