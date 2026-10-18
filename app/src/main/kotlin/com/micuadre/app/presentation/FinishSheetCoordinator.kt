package com.micuadre.app.presentation

import com.micuadre.app.domain.model.PlatformSelection
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.finish.FinishSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns when the finish-trip sheet opens: from the in-app "Terminar carrera" button and from
 * the notification deep link ([PendingFinishRequest]). JVM-testable; the view model only forwards.
 *
 * Opening needs a suspend lookup (last used platform), so the final decision is made atomically
 * inside the state update: the sheet opens only if it is still closed and the shift is still on
 * a trip. A single in-flight [openJob] absorbs repeated triggers, and [onDismissed] cancels it
 * so a dismissal during the lookup can never be followed by a reopen.
 */
class FinishSheetCoordinator(
    private val ui: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
    private val lastUsedPlatform: suspend () -> PlatformSelection,
    private val nowMs: () -> Long
) {
    companion object {
        /** How long a notification request waits for the shift state to be restored. */
        const val PENDING_TIMEOUT_MS = 3_000L
    }

    private val pending = PendingFinishRequest()
    private var expiryJob: Job? = null
    private var openJob: Job? = null

    /** Entry from outside the app (notification action). Consumed once. */
    fun onLaunchRequest(request: LaunchRequest) {
        when (request) {
            LaunchRequest.OPEN_FINISH_SHEET -> {
                pending.request()
                expiryJob?.cancel()
                expiryJob = scope.launch {
                    delay(PENDING_TIMEOUT_MS)
                    pending.expire()
                }
                onShiftState(ui.value.shiftState)
            }
        }
    }

    /** Must be called every time the shift state is (re)published. */
    fun onShiftState(state: ShiftState) {
        if (pending.onState(state) == PendingFinishRequest.Decision.OpenSheet) {
            expiryJob?.cancel()
            open()
        }
    }

    fun onFinishTapped() = open()

    /** Call when the sheet is closed by the user or after confirming. */
    fun onDismissed() {
        openJob?.cancel()
        openJob = null
    }

    private fun open() {
        if (openJob?.isActive == true) return
        if (ui.value.finishSheet != null) return
        if (ui.value.shiftState !is ShiftState.OnTrip) return
        openJob = scope.launch {
            val selection = lastUsedPlatform()
            val endedAt = nowMs()
            ui.update { current ->
                val state = current.shiftState
                if (current.finishSheet != null || state !is ShiftState.OnTrip) {
                    current
                } else {
                    current.copy(
                        finishSheet = FinishSheetState(
                            tripStartedAtMs = state.tripStartedAtMs,
                            tripEndedAtMs = endedAt,
                            initialSelection = selection
                        )
                    )
                }
            }
        }
    }
}
