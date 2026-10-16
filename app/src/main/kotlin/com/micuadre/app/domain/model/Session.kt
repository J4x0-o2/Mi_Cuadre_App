package com.micuadre.app.domain.model

/**
 * A working session (jornada). A session starts when the driver connects
 * and ends when they tap "Terminar jornada".
 */
data class Session(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?
)
