package com.micuadre.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedTimeTest {

    private val minute = 60_000L
    // 2026-01-01T08:00:00Z
    private val t0 = 1_767_254_400_000L

    private fun interval(start: Long, end: Long?) =
        ConnectedInterval(sessionId = 1L, startedAt = start, endedAt = end)

    @Test
    fun `no intervals means no connected time`() {
        assertEquals(0L, ConnectedTime.totalMs(emptyList(), nowMs = t0))
    }

    @Test
    fun `closed intervals are summed and the pause between them is excluded`() {
        val intervals = listOf(
            interval(t0, t0 + 60 * minute),
            interval(t0 + 90 * minute, t0 + 120 * minute)
        )

        assertEquals(90 * minute, ConnectedTime.totalMs(intervals, nowMs = t0 + 500 * minute))
    }

    @Test
    fun `an open interval counts up to now`() {
        val intervals = listOf(
            interval(t0, t0 + 10 * minute),
            interval(t0 + 20 * minute, null)
        )

        assertEquals(10 * minute + 5 * minute, ConnectedTime.totalMs(intervals, nowMs = t0 + 25 * minute))
    }

    @Test
    fun `an open interval never goes negative when the clock moved backwards`() {
        assertEquals(0L, ConnectedTime.totalMs(listOf(interval(t0, null)), nowMs = t0 - minute))
    }

    @Test
    fun `an interval that ends before it starts contributes nothing`() {
        assertEquals(0L, ConnectedTime.totalMs(listOf(interval(t0, t0 - minute)), nowMs = t0))
    }

    @Test
    fun `legacy intervals with an implausible start are ignored`() {
        val intervals = listOf(
            interval(5_000L, 9_000L),
            interval(5_000L, null),
            interval(t0, t0 + minute)
        )

        assertEquals(minute, ConnectedTime.totalMs(intervals, nowMs = t0 + 10 * minute))
    }

    @Test
    fun `seconds are rounded down`() {
        val intervals = listOf(interval(t0, t0 + 61_999L))

        assertEquals(61L, ConnectedTime.totalSeconds(intervals, nowMs = t0 + minute * 10))
    }
}
