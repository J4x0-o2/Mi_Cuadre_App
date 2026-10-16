package com.micuadre.app.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.usecase.EndShiftUseCase
import com.micuadre.app.domain.usecase.FinishTripUseCase
import com.micuadre.app.domain.usecase.GetDailySummaryUseCase
import com.micuadre.app.domain.usecase.RehydrateShiftStateUseCase
import com.micuadre.app.domain.usecase.StartShiftUseCase
import com.micuadre.app.domain.usecase.StartTripUseCase
import com.micuadre.app.util.TimeUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that owns the [ShiftStateMachine] for the working
 * session. Receives actions as Intents, persists results through the
 * repository use cases, and updates the persistent notification.
 *
 * Time references use [SystemClock.elapsedRealtime] for monotonicity.
 */
@AndroidEntryPoint
class ShiftService : Service() {

    @Inject lateinit var controller: ShiftServiceController
    @Inject lateinit var startShift: StartShiftUseCase
    @Inject lateinit var endShift: EndShiftUseCase
    @Inject lateinit var startTrip: StartTripUseCase
    @Inject lateinit var finishTrip: FinishTripUseCase
    @Inject lateinit var getDailySummary: GetDailySummaryUseCase
    @Inject lateinit var rehydrateShiftState: RehydrateShiftStateUseCase

    private val stateMachine = ShiftStateMachine(nowMs = { SystemClock.elapsedRealtime() })

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    private val _currentState = MutableStateFlow<ShiftState>(ShiftState.Disconnected)
    private val currentState: StateFlow<ShiftState> = _currentState.asStateFlow()

    private lateinit var notificationFactory: NotificationFactory

    /** Active session id (null when disconnected). */
    private var activeSessionId: Long? = null

    /** Trip start timestamp for the active trip (OnTrip only). */
    private var activeTripStartMs: Long? = null

    override fun onCreate() {
        super.onCreate()
        notificationFactory = NotificationFactory(
            context = applicationContext,
            nowMs = { SystemClock.elapsedRealtime() }
        )
        notificationFactory.ensureChannel()
        rehydrateFromRepository()
    }

    /**
     * Pulls the persisted shift state from Room so the freshly-recreated
     * service instance matches the controller (which survives process
     * scope). The race guard skips publishing if `handleConnect` has
     * already moved `_currentState` off `Disconnected` while the DB
     * query was in flight.
     */
    private fun rehydrateFromRepository() {
        scope.launch {
            try {
                val result = rehydrateShiftState()
                val restored = result.state
                if (restored is ShiftState.Disconnected) return@launch
                if (_currentState.value !is ShiftState.Disconnected) return@launch

                _currentState.value = restored
                activeSessionId = result.sessionId
                if (restored is ShiftState.OnTrip) {
                    activeTripStartMs = restored.tripStartedAtMs
                }
                controller.publishState(restored)
                startInForeground(restored)
            } catch (t: Throwable) {
                Log.e(TAG, "Rehydration failed; staying Disconnected", t)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY
        when (action) {
            ShiftServiceController.ACTION_CONNECT -> handleConnect()
            ShiftServiceController.ACTION_DISCONNECT -> handleDisconnect()
            ShiftServiceController.ACTION_START_TRIP -> handleStartTrip()
            ShiftServiceController.ACTION_FINISH_TRIP -> handleFinishTrip(intent)
            ShiftServiceController.ACTION_END_SHIFT -> handleEndShift()
        }
        return START_STICKY
    }

    private fun handleConnect() {
        if (_currentState.value !is ShiftState.Disconnected) return
        scope.launch {
            val now = SystemClock.elapsedRealtime()
            val sessionId = activeSessionId ?: startShift(startedAt = now).also { activeSessionId = it }
            val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.Connect)
            _currentState.value = next
            controller.publishState(next)
            startInForeground(next)
        }
    }

    private fun handleDisconnect() {
        if (_currentState.value !is ShiftState.Connected) return
        val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.Disconnect)
        _currentState.value = next
        controller.publishState(next)
        stopTicker()
        // Keep the service alive but remove the foreground notification.
        stopForeground(STOP_FOREGROUND_REMOVE)
        // The service is no longer needed when disconnected; let it die so
        // we don't hold an empty notification slot. The controller's state
        // survives process scope (singleton), so the UI remains accurate.
        stopSelf()
    }

    private fun handleStartTrip() {
        if (_currentState.value !is ShiftState.Connected) return
        val now = SystemClock.elapsedRealtime()
        scope.launch {
            val sessionId = activeSessionId ?: return@launch
            startTrip(sessionId, startedAt = now)
            val next = stateMachine.transition(
                _currentState.value,
                ShiftStateMachine.Action.StartTrip(tripStartedAtMs = now)
            )
            _currentState.value = next
            activeTripStartMs = now
            controller.publishState(next)
            updateNotification(next)
        }
    }

    private fun handleFinishTrip(intent: Intent) {
        if (_currentState.value !is ShiftState.OnTrip) return
        val sessionId = activeSessionId ?: return
        val tripStart = activeTripStartMs ?: return

        val platformName = intent.getStringExtra(ShiftServiceController.EXTRA_PLATFORM)
        val platformOther = intent.getStringExtra(ShiftServiceController.EXTRA_PLATFORM_OTHER)
        val amountMinor = intent.getLongExtra(ShiftServiceController.EXTRA_AMOUNT_MINOR, 0L)
        val paymentName = intent.getStringExtra(ShiftServiceController.EXTRA_PAYMENT_METHOD)

        val platform = platformName?.let { runCatching { Platform.valueOf(it) }.getOrNull() }
        val payment = paymentName?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }

        if (platform == null || payment == null || amountMinor <= 0L) {
            // Invalid payload; stay in OnTrip and let the UI re-render.
            return
        }

        val now = SystemClock.elapsedRealtime()
        scope.launch {
            finishTrip(
                sessionId = sessionId,
                platform = platform,
                platformOtherLabel = platformOther,
                startedAt = tripStart,
                endedAt = now,
                amountMinor = amountMinor,
                paymentMethod = payment
            )
            val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.FinishTrip)
            _currentState.value = next
            activeTripStartMs = null
            controller.publishState(next)
            updateNotification(next)
        }
    }

    private fun handleEndShift() {
        val sessionId = activeSessionId
        if (sessionId == null) {
            stopTicker()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        scope.launch {
            val now = SystemClock.elapsedRealtime()
            endShift(sessionId, endedAt = now)
            val summary = getDailySummary(sessionId)
            controller.publishSummary(summary)
            val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.EndShift)
            _currentState.value = next
            activeSessionId = null
            activeTripStartMs = null
            controller.publishState(next)
            stopTicker()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startInForeground(state: ShiftState) {
        val notification = notificationFactory.build(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationFactory.NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationFactory.NOTIF_ID, notification)
        }
        startTicker()
    }

    private fun updateNotification(state: ShiftState) {
        val notification = notificationFactory.build(state)
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(NotificationFactory.NOTIF_ID, notification)
            } catch (security: SecurityException) {
                // POST_NOTIFICATIONS not granted on API 33+; service still runs.
            }
        }
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (true) {
                val s = _currentState.value
                if (s is ShiftState.Disconnected) break
                updateNotification(s)
                delay(1_000L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // Exposed for testing only — package-private accessor.
    internal fun _stateForTest(): StateFlow<ShiftState> = currentState

    @Suppress("unused")
    private fun touchTimeUtils() = TimeUtils // keep import meaningful

    private companion object {
        private const val TAG = "ShiftService"
    }
}
