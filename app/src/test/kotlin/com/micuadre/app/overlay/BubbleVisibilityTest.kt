package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Test

class BubbleVisibilityTest {

    private val connected = ShiftState.Connected(connectedSinceMs = 1L)
    private val onTrip = ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = 2L)

    private fun show(state: ShiftState, enabled: Boolean = true, canDraw: Boolean = true) =
        BubbleVisibility.shouldShow(state, enabled, canDraw)

    @Test
    fun `shows while connected and while on a trip`() {
        assertEquals(true, show(connected))
        assertEquals(true, show(onTrip))
    }

    @Test
    fun `hides when disconnected, which includes a paused open shift`() {
        assertEquals(false, show(ShiftState.Disconnected))
    }

    @Test
    fun `hides when the toggle is off`() {
        assertEquals(false, show(connected, enabled = false))
        assertEquals(false, show(onTrip, enabled = false))
    }

    @Test
    fun `hides when the overlay permission is missing`() {
        assertEquals(false, show(connected, canDraw = false))
        assertEquals(false, show(onTrip, canDraw = false))
    }
}
