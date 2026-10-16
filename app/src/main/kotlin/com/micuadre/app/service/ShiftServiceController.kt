package com.micuadre.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-singleton bridge between ViewModel and [ShiftService].
 *
 * Holds the canonical state flow and the one-shot summary stream, and
 * dispatches actions as Intents to the running service (starting it via
 * `startForegroundService` if necessary).
 */
@Singleton
class ShiftServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        const val ACTION_CONNECT = "com.micuadre.app.action.CONNECT"
        const val ACTION_DISCONNECT = "com.micuadre.app.action.DISCONNECT"
        const val ACTION_START_TRIP = "com.micuadre.app.action.START_TRIP"
        const val ACTION_FINISH_TRIP = "com.micuadre.app.action.FINISH_TRIP"
        const val ACTION_END_SHIFT = "com.micuadre.app.action.END_SHIFT"

        const val EXTRA_PLATFORM = "extra_platform"
        const val EXTRA_PLATFORM_OTHER = "extra_platform_other"
        const val EXTRA_AMOUNT_MINOR = "extra_amount_minor"
        const val EXTRA_PAYMENT_METHOD = "extra_payment_method"
    }

    private val _state = MutableStateFlow<ShiftState>(ShiftState.Disconnected)
    val state: StateFlow<ShiftState> = _state.asStateFlow()

    private val _summaryEvents = MutableSharedFlow<DailySummary>(replay = 0, extraBufferCapacity = 4)
    val summaryEvents: SharedFlow<DailySummary> = _summaryEvents.asSharedFlow()

    private val _errorEvents = MutableSharedFlow<ShiftError>(replay = 0, extraBufferCapacity = 4)
    val errorEvents: SharedFlow<ShiftError> = _errorEvents.asSharedFlow()

    /** Called by [ShiftService] when a requested action could not be applied. */
    internal fun publishError(error: ShiftError) {
        _errorEvents.tryEmit(error)
    }

    /** Called by [ShiftService] to publish the current state. */
    internal fun publishState(newState: ShiftState) {
        _state.value = newState
    }

    /** Called by [ShiftService] to broadcast a freshly computed summary. */
    internal fun publishSummary(summary: DailySummary) {
        _summaryEvents.tryEmit(summary)
    }

    fun connect() = sendAction(ACTION_CONNECT)

    fun disconnect() = sendAction(ACTION_DISCONNECT)

    fun startTrip() = sendAction(ACTION_START_TRIP)

    fun finishTrip(
        platform: Platform,
        platformOther: String?,
        amountMinor: Long,
        method: PaymentMethod
    ) {
        val extras = Bundle().apply {
            putString(EXTRA_PLATFORM, platform.name)
            putString(EXTRA_PLATFORM_OTHER, platformOther)
            putLong(EXTRA_AMOUNT_MINOR, amountMinor)
            putString(EXTRA_PAYMENT_METHOD, method.name)
        }
        sendAction(ACTION_FINISH_TRIP, extras)
    }

    fun endShift() = sendAction(ACTION_END_SHIFT)

    private fun sendAction(action: String, extras: Bundle? = null) {
        val intent = Intent(context, ShiftService::class.java).apply {
            this.action = action
            if (extras != null) putExtras(extras)
        }
        ContextCompat.startForegroundService(context, intent)
    }
}

/** One-shot failures the service reports back to the UI. */
enum class ShiftError {
    /** Finish-trip payload was invalid or could not be saved; the trip stays open. */
    InvalidFinishTrip
}
