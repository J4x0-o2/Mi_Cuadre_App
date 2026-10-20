package com.micuadre.app.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.micuadre.app.MainActivity
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.ThemeSchedule
import com.micuadre.app.domain.repository.ThemePreferenceRepository
import com.micuadre.app.presentation.LaunchRouting
import com.micuadre.app.presentation.theme.themeStates
import com.micuadre.app.service.ShiftServiceController
import com.micuadre.app.ui.theme.MiCuadreTheme
import com.micuadre.app.util.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Draws the bubble over other apps with a TYPE_APPLICATION_OVERLAY window that hosts a Compose
 * view. All gesture, placement and panel decisions live in the pure classes of this package;
 * this class only moves the window and forwards events. Call it from the main thread (the
 * service does).
 *
 * The window is never focusable, so it does not steal the keyboard from the app underneath, and
 * it is not touch-modal, so touches outside it reach that app (and collapse the panel).
 */
class WindowBubbleOverlay @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: ShiftServiceController,
    private val themePreferences: ThemePreferenceRepository,
    private val clock: Clock
) : BubbleOverlay {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val bubbleSizePx = (BubbleDimens.BUBBLE_DP * density).toInt()
    private val panelWidthPx = (BubbleDimens.PANEL_WIDTH_DP * density).toInt()
    private val panelHeightPx = (BubbleDimens.PANEL_HEIGHT_ESTIMATE_DP * density).toInt()

    private val intervals = MutableStateFlow<List<ConnectedInterval>>(emptyList())
    private val commands = object : BubbleCommands {
        override fun startTrip() = controller.startTrip()
        override fun cancelTrip(tripStartedAtMs: Long) = controller.cancelTrip(tripStartedAtMs)
        override fun openFinishTrip() = openApp(LaunchRouting.ACTION_OPEN_FINISH_TRIP)
        override fun openApp() = openApp(null)
    }

    /** Last dragged/snapped bubble position; survives hide/show within the process. */
    private var bubblePosition: BubblePosition? = null

    private var view: BubbleRootView? = null
    private var params: WindowManager.LayoutParams? = null
    private var owner: OverlayLifecycleOwner? = null
    private var scope: CoroutineScope? = null
    private var coordinator: BubblePanelCoordinator? = null
    private var animator: ValueAnimator? = null
    private val gesture = DragGesture(ViewConfiguration.get(context).scaledTouchSlop.toFloat())

    override fun show(state: ShiftState, intervals: List<ConnectedInterval>) {
        this.intervals.value = intervals
        if (view == null && !attach()) return
        coordinator?.onShiftState(state)
    }

    override fun hide() {
        val attached = view ?: return
        animator?.cancel()
        animator = null
        scope?.cancel()
        scope = null
        try {
            windowManager.removeView(attached)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bubble view was already gone", e)
        }
        owner?.stop()
        owner = null
        view = null
        params = null
        coordinator = null
    }

    private fun attach(): Boolean {
        val screen = screenBounds()
        val start = bubblePosition?.let { BubblePlacement.clamp(it, bubbleSizePx, screen) }
            ?: BubblePlacement.initial(bubbleSizePx, screen)
        bubblePosition = start

        val panelCoordinator = BubblePanelCoordinator(commands)
        val lifecycleOwner = OverlayLifecycleOwner().also { it.start() }
        val composeView = BubbleRootView(context).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            onOutsideTouch = { panelCoordinator.onCollapse() }
            onConfigChanged = { onConfigurationChanged() }
            setContent { Root(panelCoordinator) }
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = start.x
            y = start.y
        }
        try {
            windowManager.addView(composeView, layoutParams)
        } catch (e: RuntimeException) {
            // Permission revoked between the check and now, or the window token is unusable.
            Log.e(TAG, "Could not add the bubble window", e)
            lifecycleOwner.stop()
            return false
        }

        val overlayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        view = composeView
        params = layoutParams
        owner = lifecycleOwner
        scope = overlayScope
        coordinator = panelCoordinator
        overlayScope.launch {
            panelCoordinator.ui.map { it.expanded }.distinctUntilChanged().collect { applyExpanded(it) }
        }
        return true
    }

    @Composable
    private fun Root(panelCoordinator: BubblePanelCoordinator) {
        val ui by panelCoordinator.ui.collectAsState()
        val currentIntervals by intervals.collectAsState()
        val theme by remember { themeStates(themePreferences.themeMode, clock, ZoneId::systemDefault) }
            .collectAsState(initial = null)
        val systemDark = isSystemInDarkTheme()
        val dark = theme?.let { ThemeSchedule.isDarkAt(it.mode, systemDark, it.nowMs, ZoneId.systemDefault()) }
            ?: systemDark
        MiCuadreTheme(darkTheme = dark) {
            BubbleContent(
                ui = ui,
                intervals = currentIntervals,
                nowMs = clock::nowMs,
                onAction = panelCoordinator::onAction,
                onCollapse = panelCoordinator::onCollapse
            )
        }
    }

    /** Swaps between the bubble and the panel placement, and who handles raw touches. */
    private fun applyExpanded(expanded: Boolean) {
        val composeView = view ?: return
        animator?.cancel()
        composeView.collapsedTouchHandler = if (expanded) null else ::onBubbleTouch
        place(target = windowPosition(expanded))
    }

    private fun windowPosition(expanded: Boolean): BubblePosition {
        val bubble = bubblePosition ?: return BubblePosition(0, 0)
        return if (expanded) {
            BubblePlacement.expanded(bubble, bubbleSizePx, panelWidthPx, panelHeightPx, screenBounds())
        } else {
            bubble
        }
    }

    private fun onBubbleTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                gesture.onDown(event.rawX, event.rawY, bubblePosition ?: BubblePosition(0, 0))
            }
            MotionEvent.ACTION_MOVE -> gesture.onMove(event.rawX, event.rawY)?.let {
                bubblePosition = it
                place(it)
            }
            MotionEvent.ACTION_UP -> when (val outcome = gesture.onUp()) {
                DragGesture.Outcome.Tap -> coordinator?.onBubbleTapped()
                is DragGesture.Outcome.Drop -> settle(outcome.position)
            }
            MotionEvent.ACTION_CANCEL -> (gesture.onUp() as? DragGesture.Outcome.Drop)?.let { settle(it.position) }
        }
        return true
    }

    /** Glides the dropped bubble to the nearest screen edge. */
    private fun settle(from: BubblePosition) {
        val target = BubblePlacement.snap(from, bubbleSizePx, screenBounds())
        bubblePosition = target
        animator?.cancel()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = SNAP_MS
            addUpdateListener {
                val t = it.animatedFraction
                place(BubblePosition(lerp(from.x, target.x, t), lerp(from.y, target.y, t)))
            }
            start()
        }
    }

    private fun onConfigurationChanged() {
        val bubble = bubblePosition ?: return
        val screen = screenBounds()
        bubblePosition = BubblePlacement.snap(BubblePlacement.clamp(bubble, bubbleSizePx, screen), bubbleSizePx, screen)
        place(windowPosition(coordinator?.ui?.value?.expanded == true))
    }

    private fun place(target: BubblePosition) {
        val composeView = view ?: return
        val layoutParams = params ?: return
        layoutParams.x = target.x
        layoutParams.y = target.y
        try {
            windowManager.updateViewLayout(composeView, layoutParams)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Bubble view is no longer attached", e)
        }
    }

    private fun screenBounds(): ScreenBounds {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bars = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            return ScreenBounds(metrics.bounds.width(), metrics.bounds.height(), bars.top, bars.bottom)
        }
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(real)
        val statusBarId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBar = if (statusBarId > 0) context.resources.getDimensionPixelSize(statusBarId) else 0
        return ScreenBounds(real.widthPixels, real.heightPixels, statusBar, 0)
    }

    private fun openApp(action: String?) {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Could not open the app from the bubble", e)
        }
    }

    private fun lerp(from: Int, to: Int, t: Float): Int = from + ((to - from) * t).toInt()

    private companion object {
        const val TAG = "BubbleOverlay"
        const val SNAP_MS = 160L
        const val FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
}
