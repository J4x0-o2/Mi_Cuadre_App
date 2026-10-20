package com.micuadre.app.overlay

import kotlin.math.hypot

/**
 * Tells a tap from a drag on the bubble. Works on raw (screen) coordinates so the window moving
 * under the finger does not feed back into the measurement.
 */
class DragGesture(private val touchSlopPx: Float) {

    sealed interface Outcome {
        data object Tap : Outcome
        data class Drop(val position: BubblePosition) : Outcome
    }

    private var downX = 0f
    private var downY = 0f
    private var origin = BubblePosition(0, 0)
    private var active = false
    private var dragging = false
    private var current = origin

    fun onDown(rawX: Float, rawY: Float, origin: BubblePosition) {
        downX = rawX
        downY = rawY
        this.origin = origin
        current = origin
        active = true
        dragging = false
    }

    /** The new window position once the finger left the slop radius, or null while it is still a tap. */
    fun onMove(rawX: Float, rawY: Float): BubblePosition? {
        if (!active) return null
        val dx = rawX - downX
        val dy = rawY - downY
        if (!dragging && hypot(dx, dy) <= touchSlopPx) return null
        dragging = true
        current = BubblePosition(origin.x + dx.toInt(), origin.y + dy.toInt())
        return current
    }

    fun onUp(): Outcome {
        val outcome = if (dragging) Outcome.Drop(current) else Outcome.Tap
        active = false
        dragging = false
        return outcome
    }
}
