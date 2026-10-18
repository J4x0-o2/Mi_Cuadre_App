package com.micuadre.app.domain.model

import com.micuadre.app.util.TimeUtils

/** Pure "Conectado" arithmetic over a session's [ConnectedInterval]s. */
object ConnectedTime {

    /**
     * Total connected milliseconds: closed intervals plus any open one counted up to [nowMs].
     * Intervals with an implausible start (legacy boot-relative data) are ignored, and an
     * interval never contributes a negative amount.
     */
    fun totalMs(intervals: List<ConnectedInterval>, nowMs: Long): Long =
        intervals
            .filter { TimeUtils.isPlausibleEpoch(it.startedAt) }
            .sumOf { TimeUtils.elapsedMs(it.startedAt, it.endedAt ?: nowMs) }

    /** [totalMs] in whole seconds, rounded down. */
    fun totalSeconds(intervals: List<ConnectedInterval>, nowMs: Long): Long =
        TimeUtils.msToSeconds(totalMs(intervals, nowMs))
}
