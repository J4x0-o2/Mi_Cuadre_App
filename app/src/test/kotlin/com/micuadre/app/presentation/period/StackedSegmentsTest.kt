package com.micuadre.app.presentation.period

import org.junit.Assert.assertEquals
import org.junit.Test

class StackedSegmentsTest {

    private val min = 0.05f

    @Test
    fun `segments above the minimum keep their fractions`() {
        val s = StackedSegments.of(cost = 0.3f, net = 0.5f, min = min)

        assertEquals(0.3f, s.cost, EPS)
        assertEquals(0.5f, s.net, EPS)
    }

    @Test
    fun `a zero segment stays hidden`() {
        val s = StackedSegments.of(cost = 0f, net = 0.4f, min = min)

        assertEquals(0f, s.cost, EPS)
        assertEquals(0.4f, s.net, EPS)
    }

    @Test
    fun `a tiny segment is raised to the minimum`() {
        val s = StackedSegments.of(cost = 0.01f, net = 0.4f, min = min)

        assertEquals(min, s.cost, EPS)
    }

    @Test
    fun `raising a tiny segment never makes the bar taller than the area`() {
        val s = StackedSegments.of(cost = 0.01f, net = 0.99f, min = min)

        assertEquals(min, s.cost, EPS)
        assertEquals(1f - min, s.net, EPS)
        assertEquals(1f, s.cost + s.net, EPS)
    }

    @Test
    fun `the total keeps the bar height when there is room`() {
        val s = StackedSegments.of(cost = 0.01f, net = 0.5f, min = min)

        // The minimum is taken from the larger segment, so the bar keeps its real height.
        assertEquals(0.51f, s.cost + s.net, EPS)
    }

    @Test
    fun `two tiny segments both show at the minimum`() {
        val s = StackedSegments.of(cost = 0.01f, net = 0.01f, min = min)

        assertEquals(min, s.cost, EPS)
        assertEquals(min, s.net, EPS)
    }

    private companion object {
        const val EPS = 0.0001f
    }
}
