package com.micuadre.app.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
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
        const val ACTION_CANCEL_TRIP = "com.micuadre.app.action.CANCEL_TRIP"
        const val ACTION_END_SHIFT = "com.micuadre.app.action.END_SHIFT"
        const val ACTION_RESTORE = "com.micuadre.app.action.RESTORE"

        const val EXTRA_PLATFORM_ID = "extra_platform_id"
        const val EXTRA_AMOUNT_MINOR = "extra_amount_minor"
        const val EXTRA_PAYMENT_METHOD = "extra_payment_method"
        const val EXTRA_TRIP_STARTED_AT_MS = "extra_trip_started_at_ms"
        const val EXTRA_DEDUCTION_IDS = "extra_deduction_ids"
        const val EXTRA_DEDUCTION_AMOUNTS = "extra_deduction_amounts"

        /** Extras of an end-shift intent: parallel id and amount arrays (nothing when [amounts] is empty). */
        fun endShiftExtras(amounts: Map<Long, Long>): Bundle = Bundle().apply {
            if (amounts.isEmpty()) return@apply
            putLongArray(EXTRA_DEDUCTION_IDS, amounts.keys.toLongArray())
            putLongArray(EXTRA_DEDUCTION_AMOUNTS, amounts.values.toLongArray())
        }

        /** Deduction amounts carried by an end-shift intent; empty when absent or the arrays do not pair up. */
        fun deductionAmountsFrom(intent: Intent): Map<Long, Long> {
            val extras = intent.extras ?: return emptyMap()
            val ids = extras.getLongArray(EXTRA_DEDUCTION_IDS) ?: return emptyMap()
            val amounts = extras.getLongArray(EXTRA_DEDUCTION_AMOUNTS) ?: return emptyMap()
            if (ids.size != amounts.size) return emptyMap()
            return ids.indices.associate { ids[it] to amounts[it] }
        }

        /** Platform id carried by a finish-trip intent, or null when absent, mistyped or not positive. */
        fun platformIdFrom(intent: Intent): Long? {
            val id = intent.extras?.get(EXTRA_PLATFORM_ID) as? Long ?: return null
            return id.takeIf { it > 0L }
        }

        /** Start time of the ride a cancel-trip intent targets, or null when absent or mistyped. */
        fun tripStartedAtFrom(intent: Intent): Long? =
            intent.extras?.get(EXTRA_TRIP_STARTED_AT_MS) as? Long
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

    /** Asks the service to rehydrate a persisted shift; it stops by itself if there is none. */
    fun restore() = sendAction(ACTION_RESTORE)

    fun disconnect() = sendAction(ACTION_DISCONNECT)

    fun startTrip() = sendAction(ACTION_START_TRIP)

    fun finishTrip(
        platformId: Long,
        amountMinor: Long,
        method: PaymentMethod
    ) {
        val extras = Bundle().apply {
            putLong(EXTRA_PLATFORM_ID, platformId)
            putLong(EXTRA_AMOUNT_MINOR, amountMinor)
            putString(EXTRA_PAYMENT_METHOD, method.name)
        }
        sendAction(ACTION_FINISH_TRIP, extras)
    }

    /** Discards the ride that started at [tripStartedAtMs]; the service ignores it if that ride is gone. */
    fun cancelTrip(tripStartedAtMs: Long) {
        val extras = Bundle().apply { putLong(EXTRA_TRIP_STARTED_AT_MS, tripStartedAtMs) }
        sendAction(ACTION_CANCEL_TRIP, extras)
    }

    /** Ends the shift; [amounts] (deduction id to minor units) are stored with it before its summary is built. */
    fun endShift(amounts: Map<Long, Long> = emptyMap()) =
        sendAction(ACTION_END_SHIFT, endShiftExtras(amounts))

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
    InvalidFinishTrip,

    /** The ride could not be saved when starting it; the shift stays Connected. */
    StartTripFailed
}
