package com.micuadre.app.presentation.period

/** Heights of a stacked bar's cost and net segments, as fractions of the bar area. */
data class StackedSegments(val cost: Float, val net: Float) {

    companion object {
        /**
         * Raises every non-zero segment to [min] so a small day stays visible next to a tall one,
         * taking the added height from the larger segment so the bar keeps its real height and
         * never grows past the bar area (1f). Two tiny segments both show at [min].
         */
        fun of(cost: Float, net: Float, min: Float): StackedSegments {
            var c = if (cost > 0f) maxOf(cost, min) else 0f
            var n = if (net > 0f) maxOf(net, min) else 0f
            val added = (c - cost) + (n - net)
            if (c >= n) c = maxOf(if (cost > 0f) min else 0f, c - added)
            else n = maxOf(if (net > 0f) min else 0f, n - added)
            val overflow = c + n - 1f
            if (overflow > 0f) {
                if (c >= n) c -= overflow else n -= overflow
            }
            return StackedSegments(c, n)
        }
    }
}
