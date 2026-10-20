package com.micuadre.app.overlay

import android.content.Context
import android.content.res.Configuration
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * Root view of the overlay window (ComposeView is final, so it is hosted in a FrameLayout). It
 * sees the raw touches first: while the bubble is collapsed they drive the drag/tap gesture
 * (Compose never sees them), and a touch outside the expanded panel arrives as ACTION_OUTSIDE.
 */
class BubbleRootView(context: Context) : FrameLayout(context) {

    /** Set while the collapsed bubble is showing; returns true when it consumed the event. */
    var collapsedTouchHandler: ((MotionEvent) -> Boolean)? = null
    var onOutsideTouch: (() -> Unit)? = null
    var onConfigChanged: (() -> Unit)? = null

    private val compose = ComposeView(context).also { addView(it) }

    fun setContent(content: @Composable () -> Unit) = compose.setContent(content)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onOutsideTouch?.invoke()
            return true
        }
        collapsedTouchHandler?.let { return it(ev) }
        return super.dispatchTouchEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        onConfigChanged?.invoke()
    }
}
