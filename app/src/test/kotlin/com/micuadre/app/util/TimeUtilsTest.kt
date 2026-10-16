package com.micuadre.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeUtilsTest {

    private val epoch2026 = 1_790_000_000_000L // a realistic epoch-millis value

    @Test
    fun `elapsedMs is the wall-clock difference`() {
        assertEquals(5_000L, TimeUtils.elapsedMs(sinceMs = epoch2026, nowMs = epoch2026 + 5_000L))
    }

    @Test
    fun `elapsedMs is clamped at zero when the clock moved backwards`() {
        assertEquals(0L, TimeUtils.elapsedMs(sinceMs = epoch2026, nowMs = epoch2026 - 1L))
    }

    @Test
    fun `elapsedSeconds rounds down`() {
        assertEquals(1L, TimeUtils.elapsedSeconds(sinceMs = 0L, nowMs = 1_999L))
    }

    @Test
    fun `system clock reports epoch millis not boot-relative time`() {
        val before = System.currentTimeMillis()
        val value = SystemWallClock().nowMs()
        val after = System.currentTimeMillis()
        assertTrue(value in before..after)
    }
}
