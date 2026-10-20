package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.ShiftState

/** The floating bubble, as the service sees it. */
interface BubbleOverlay {
    /** Adds the bubble if needed and shows [state]; safe to call repeatedly. */
    fun show(state: ShiftState, intervals: List<ConnectedInterval>)

    /** Removes the bubble if it is on screen. */
    fun hide()
}
