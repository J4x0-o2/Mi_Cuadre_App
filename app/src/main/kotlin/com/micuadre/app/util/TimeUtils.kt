package com.micuadre.app.util

import java.util.concurrent.TimeUnit

/**
 * Pure time helpers. All timestamps are wall-clock epoch milliseconds
 * supplied by a [Clock]; nothing here reads a clock directly.
 */
object TimeUtils {

    /**
     * 2020-01-01T00:00:00Z. Any persisted timestamp earlier than this cannot be
     * a real wall-clock value from this app; it is a boot-relative uptime
     * written by the pre-wall-clock build.
     */
    const val MIN_PLAUSIBLE_EPOCH_MS = 1_577_836_800_000L

    /** True when [epochMs] looks like a real wall-clock epoch-millis value. */
    fun isPlausibleEpoch(epochMs: Long): Boolean = epochMs >= MIN_PLAUSIBLE_EPOCH_MS

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
