package com.micuadre.app.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePlacementTest {

    // 1080x2400 screen, 100 px of status bar on top, 120 px of navigation bar at the bottom.
    private val screen = ScreenBounds(width = 1080, height = 2400, insetTop = 100, insetBottom = 120)
    private val size = 168

    @Test
    fun `releasing on the left half snaps to the left edge`() {
        val snapped = BubblePlacement.snap(BubblePosition(x = 300, y = 800), size, screen)

        assertEquals(BubblePosition(x = 0, y = 800), snapped)
    }

    @Test
    fun `releasing on the right half snaps to the right edge`() {
        val snapped = BubblePlacement.snap(BubblePosition(x = 700, y = 800), size, screen)

        assertEquals(BubblePosition(x = 1080 - size, y = 800), snapped)
    }

    @Test
    fun `the bubble centre decides the side, not its left corner`() {
        // Centre at 540 + 1: just right of the middle.
        val right = BubblePlacement.snap(BubblePosition(x = 540 + 1 - size / 2, y = 500), size, screen)
        val left = BubblePlacement.snap(BubblePosition(x = 540 - 1 - size / 2, y = 500), size, screen)

        assertEquals(1080 - size, right.x)
        assertEquals(0, left.x)
    }

    @Test
    fun `snapping clamps the vertical position inside the usable screen`() {
        assertEquals(100, BubblePlacement.snap(BubblePosition(x = 0, y = -400), size, screen).y)
        assertEquals(2400 - 120 - size, BubblePlacement.snap(BubblePosition(x = 0, y = 5000), size, screen).y)
    }

    @Test
    fun `a bubble dragged past the screen sides still lands on an edge`() {
        assertEquals(0, BubblePlacement.snap(BubblePosition(x = -90, y = 800), size, screen).x)
        assertEquals(1080 - size, BubblePlacement.snap(BubblePosition(x = 1500, y = 800), size, screen).x)
    }

    @Test
    fun `clamp keeps a position fully inside the screen without snapping to an edge`() {
        assertEquals(BubblePosition(500, 800), BubblePlacement.clamp(BubblePosition(500, 800), size, screen))
        assertEquals(BubblePosition(1080 - size, 100), BubblePlacement.clamp(BubblePosition(2000, -5), size, screen))
    }

    @Test
    fun `the first position is on the right edge a quarter of the way down`() {
        val initial = BubblePlacement.initial(size, screen)

        assertEquals(1080 - size, initial.x)
        assertEquals(100 + (2400 - 100 - 120 - size) / 4, initial.y)
    }

    @Test
    fun `a screen smaller than the bubble does not crash the clamp`() {
        val tiny = ScreenBounds(width = 100, height = 100, insetTop = 20, insetBottom = 20)

        val snapped = BubblePlacement.snap(BubblePosition(x = 50, y = 50), size, tiny)

        assertEquals(BubblePosition(x = 0, y = 20), snapped)
    }

    @Test
    fun `the expanded panel aligns with the side of a left-snapped bubble`() {
        val panel = BubblePlacement.expanded(BubblePosition(0, 800), size, panelWidth = 700, panelHeight = 600, screen)

        assertEquals(BubblePosition(0, 800), panel)
    }

    @Test
    fun `the expanded panel aligns with the side of a right-snapped bubble`() {
        val panel = BubblePlacement.expanded(
            BubblePosition(1080 - size, 800), size, panelWidth = 700, panelHeight = 600, screen
        )

        assertEquals(BubblePosition(1080 - 700, 800), panel)
    }

    @Test
    fun `the expanded panel moves up when it would fall below the screen`() {
        val panel = BubblePlacement.expanded(BubblePosition(0, 2100), size, panelWidth = 700, panelHeight = 600, screen)

        assertEquals(2400 - 120 - 600, panel.y)
    }
}
