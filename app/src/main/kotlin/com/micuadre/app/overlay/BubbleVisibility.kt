package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ShiftState

/** Decides whether the floating bubble is on screen. */
object BubbleVisibility {

    /**
     * Shown only while a shift is live (connected or on a trip), the user turned the bubble on and
     * the system lets the app draw over other apps. A paused shift is [ShiftState.Disconnected].
     */
    fun shouldShow(state: ShiftState, enabled: Boolean, canDrawOverlays: Boolean): Boolean =
        enabled && canDrawOverlays && state !is ShiftState.Disconnected
}
