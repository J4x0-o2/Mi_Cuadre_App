package com.micuadre.app.overlay

/** Top-left corner of the bubble window, in screen pixels. */
data class BubblePosition(val x: Int, val y: Int)

/** Screen size in pixels plus the bands the bubble must stay out of (status and navigation bars). */
data class ScreenBounds(
    val width: Int,
    val height: Int,
    val insetTop: Int = 0,
    val insetBottom: Int = 0
)

/** Pure placement math for the floating bubble and its expanded panel. */
object BubblePlacement {

    /** First position: right edge, a quarter of the way down the usable height. */
    fun initial(bubbleSize: Int, screen: ScreenBounds): BubblePosition =
        BubblePosition(
            x = maxX(bubbleSize, screen),
            y = screen.insetTop + (usableHeight(screen) - bubbleSize).coerceAtLeast(0) / 4
        )

    /** Moves the bubble to the horizontal edge nearest to its centre and keeps it vertically on screen. */
    fun snap(position: BubblePosition, bubbleSize: Int, screen: ScreenBounds): BubblePosition {
        val centreX = position.x + bubbleSize / 2
        val x = if (centreX < screen.width / 2) 0 else maxX(bubbleSize, screen)
        return BubblePosition(x, clampY(position.y, bubbleSize, screen))
    }

    /** Keeps the bubble fully on screen without moving it to an edge (e.g. after a rotation). */
    fun clamp(position: BubblePosition, bubbleSize: Int, screen: ScreenBounds): BubblePosition =
        BubblePosition(
            x = position.x.coerceIn(0, maxX(bubbleSize, screen)),
            y = clampY(position.y, bubbleSize, screen)
        )

    /**
     * Where the expanded panel goes: on the same side of the screen as the bubble it grows from,
     * level with it, and pushed up when it would overflow the bottom.
     */
    fun expanded(
        bubble: BubblePosition,
        bubbleSize: Int,
        panelWidth: Int,
        panelHeight: Int,
        screen: ScreenBounds
    ): BubblePosition {
        val onLeft = bubble.x + bubbleSize / 2 < screen.width / 2
        val x = if (onLeft) bubble.x else bubble.x + bubbleSize - panelWidth
        return BubblePosition(
            x = x.coerceIn(0, (screen.width - panelWidth).coerceAtLeast(0)),
            y = clampY(bubble.y, panelHeight, screen)
        )
    }

    private fun maxX(size: Int, screen: ScreenBounds) = (screen.width - size).coerceAtLeast(0)

    private fun usableHeight(screen: ScreenBounds) = screen.height - screen.insetTop - screen.insetBottom

    private fun clampY(y: Int, size: Int, screen: ScreenBounds): Int {
        val max = (screen.height - screen.insetBottom - size).coerceAtLeast(screen.insetTop)
        return y.coerceIn(screen.insetTop, max)
    }
}
