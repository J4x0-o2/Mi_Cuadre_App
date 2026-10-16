package com.micuadre.app.util

import android.os.SystemClock
import java.util.concurrent.TimeUnit

/**
 * Time helpers backed by [SystemClock.elapsedRealtime] so the source of
 * truth is monotonic and survives deep sleep.
 */
object TimeUtils {

    /**
     * Returns the elapsed milliseconds since the supplied reference.
     * Clamped at 0.
     */
    fun elapsedSince(referenceMs: Long): Long {
        val now = SystemClock.elapsedRealtime()
        return (now - referenceMs).coerceAtLeast(0L)
    }

    /**
     * Converts milliseconds to whole seconds, rounding down.
     */
    fun msToSeconds(ms: Long): Long = TimeUnit.MILLISECONDS.toSeconds(ms)
}
