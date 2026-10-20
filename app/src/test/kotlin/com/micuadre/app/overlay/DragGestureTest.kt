package com.micuadre.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DragGestureTest {

    private val gesture = DragGesture(touchSlopPx = 20f)
    private val origin = BubblePosition(x = 100, y = 400)

    @Test
    fun `lifting without moving is a tap`() {
        gesture.onDown(500f, 900f, origin)

        assertEquals(DragGesture.Outcome.Tap, gesture.onUp())
    }

    @Test
    fun `a wobble within the touch slop is still a tap`() {
        gesture.onDown(500f, 900f, origin)

        assertNull(gesture.onMove(510f, 912f))
        assertEquals(DragGesture.Outcome.Tap, gesture.onUp())
    }

    @Test
    fun `moving past the slop starts a drag that follows the finger`() {
        gesture.onDown(500f, 900f, origin)

        val position = gesture.onMove(560f, 880f)

        assertEquals(BubblePosition(x = 160, y = 380), position)
    }

    @Test
    fun `lifting after a drag drops the bubble where it was dragged`() {
        gesture.onDown(500f, 900f, origin)
        gesture.onMove(600f, 1000f)

        assertEquals(DragGesture.Outcome.Drop(BubblePosition(x = 200, y = 500)), gesture.onUp())
    }

    @Test
    fun `once dragging, coming back near the start is not a tap`() {
        gesture.onDown(500f, 900f, origin)
        gesture.onMove(600f, 1000f)
        gesture.onMove(502f, 901f)

        assertEquals(DragGesture.Outcome.Drop(BubblePosition(x = 102, y = 401)), gesture.onUp())
    }

    @Test
    fun `moves before a touch down are ignored`() {
        assertNull(gesture.onMove(900f, 900f))
    }

    @Test
    fun `a new touch starts a fresh gesture`() {
        gesture.onDown(500f, 900f, origin)
        gesture.onMove(700f, 900f)
        gesture.onUp()

        gesture.onDown(10f, 10f, origin)

        assertEquals(DragGesture.Outcome.Tap, gesture.onUp())
    }
}
