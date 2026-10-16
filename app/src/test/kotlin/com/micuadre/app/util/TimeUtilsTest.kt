package com.micuadre.app.util

import com.micuadre.app.domain.model.ShiftState
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
    fun `rehydrated shift keeps counting from persisted wall-clock start across a reboot`() {
        // Session persisted at epoch2026; process died and the device rebooted.
        // A boot-relative clock would restart near 0; wall clock keeps going.
        val clock = FakeClock(now = epoch2026)
        val state = ShiftState.Connected(connectedSinceMs = clock.nowMs())

        clock.now = epoch2026 + 3L * 60 * 60 * 1000 // three hours later

        assertEquals(10_800L, TimeUtils.elapsedSeconds(state.connectedSinceMs, clock.nowMs()))
    }

    @Test
    fun `system clock reports epoch millis not boot-relative time`() {
        val before = System.currentTimeMillis()
        val value = SystemWallClock().nowMs()
        val after = System.currentTimeMillis()
        assertTrue(value in before..after)
    }
}
