package com.micuadre.app.util

import java.util.concurrent.TimeUnit

/**
 * Pure time helpers. All timestamps are wall-clock epoch milliseconds
 * supplied by a [Clock]; nothing here reads a clock directly.
 */
object TimeUtils {

    /** Milliseconds from [sinceMs] to [nowMs], clamped at 0 if the clock moved backwards. */
    fun elapsedMs(sinceMs: Long, nowMs: Long): Long = (nowMs - sinceMs).coerceAtLeast(0L)

    /** Whole seconds from [sinceMs] to [nowMs], rounded down, clamped at 0. */
    fun elapsedSeconds(sinceMs: Long, nowMs: Long): Long =
        TimeUnit.MILLISECONDS.toSeconds(elapsedMs(sinceMs, nowMs))

    /**
     * Converts milliseconds to whole seconds, rounding down.
     */
    fun msToSeconds(ms: Long): Long = TimeUnit.MILLISECONDS.toSeconds(ms)
}
