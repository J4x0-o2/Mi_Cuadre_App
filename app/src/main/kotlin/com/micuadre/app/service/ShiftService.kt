package com.micuadre.app.service

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.repository.BubblePreferenceRepository
import com.micuadre.app.overlay.BubbleOverlay
import com.micuadre.app.overlay.BubbleVisibility
import com.micuadre.app.overlay.OverlayPermission
import com.micuadre.app.overlay.permissionUpdates
import com.micuadre.app.domain.usecase.CancelTripUseCase
import com.micuadre.app.domain.usecase.EndShiftUseCase
import com.micuadre.app.domain.usecase.GetDailySummaryUseCase
import com.micuadre.app.domain.usecase.RehydrateShiftStateUseCase
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.domain.usecase.StartShiftUseCase
import com.micuadre.app.domain.usecase.StartTripUseCase
import com.micuadre.app.util.Clock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Foreground service that owns the [ShiftStateMachine] for the working
 * session. Receives actions as Intents, persists results through the
 * repository use cases, and updates the persistent notification.
 *
 * Time references are wall-clock epoch milliseconds from [Clock], so persisted
 * values survive process death and reboots.
 */
@AndroidEntryPoint
class ShiftService : Service() {

    @Inject lateinit var clock: Clock
    @Inject lateinit var controller: ShiftServiceController
    @Inject lateinit var startShift: StartShiftUseCase
    @Inject lateinit var endShift: EndShiftUseCase
    @Inject lateinit var startTrip: StartTripUseCase
    @Inject lateinit var cancelTrip: CancelTripUseCase
    @Inject lateinit var finishTripHandler: FinishTripHandler
    @Inject lateinit var getDailySummary: GetDailySummaryUseCase
    @Inject lateinit var rehydrateShiftState: RehydrateShiftStateUseCase
    @Inject lateinit var repository: ShiftRepository
    @Inject lateinit var deductions: DeductionRepository
    @Inject lateinit var bubblePreferences: BubblePreferenceRepository
    @Inject lateinit var overlayPermission: OverlayPermission
    @Inject lateinit var bubbleOverlay: BubbleOverlay
    @Inject lateinit var foregroundStarter: ForegroundStarter

    private val stateMachine by lazy { ShiftStateMachine(nowMs = clock::nowMs) }
    private val finishTripReducer by lazy { FinishTripReducer(stateMachine) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var totalsJob: Job? = null
    private var intervalsJob: Job? = null
    private val connectedIntervals = MutableStateFlow<List<ConnectedInterval>>(emptyList())
    private val intervalLock = Mutex()
    private var dayTotals = DayTotals.EMPTY

    private val _currentState = MutableStateFlow<ShiftState>(ShiftState.Disconnected)
    private val currentState: StateFlow<ShiftState> = _currentState.asStateFlow()

    internal lateinit var notificationFactory: NotificationFactory

    /** Active session id (null when disconnected). */
    private var activeSessionId: Long? = null

    /** Trip start timestamp for the active trip (OnTrip only). */
    private var activeTripStartMs: Long? = null

    /** Id of the persisted open trip row for the active trip (OnTrip only). */
    private var activeTripId: Long? = null

    private var rehydrated = false

    /** A start is being persisted; further starts are ignored until it settles (main thread only). */
    private var startInFlight = false

    /** Completes once rehydration has settled (restored or not), for actions that must wait for it. */
    private val rehydration = CompletableDeferred<Unit>()
    private var connectPending = false
    private var startCommandSeen = false

    /** A disconnect arrived before rehydration finished: the restored shift must stay paused. */
    private var disconnectedBeforeRehydration = false

    override fun onCreate() {
        super.onCreate()
        notificationFactory = NotificationFactory(applicationContext)
        notificationFactory.ensureChannel()
        observeBubble()
        rehydrateFromRepository()
    }

    /**
     * Keeps the floating bubble in sync with the shift state, the settings toggle and the overlay
     * permission. The permission has no callback, so while it is missing it is re-checked
     * periodically; that lets the bubble appear right after the user grants it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeBubble() {
        scope.launch {
            try {
                combine(_currentState, bubbleGate(), connectedIntervals) { state, gate, intervals ->
                    Triple(state, gate, intervals)
                }.collect { (state, gate, intervals) ->
                    if (BubbleVisibility.shouldShow(state, gate.enabled, gate.canDrawOverlays)) {
                        bubbleOverlay.show(state, intervals)
                    } else {
                        bubbleOverlay.hide()
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e(TAG, "Bubble observation failed; the bubble stays off", t)
                bubbleOverlay.hide()
            }
        }
    }

    private data class BubbleGate(val enabled: Boolean, val canDrawOverlays: Boolean)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun bubbleGate(): Flow<BubbleGate> = bubblePreferences.bubbleEnabled
        .distinctUntilChanged()
        .flatMapLatest { enabled ->
            if (!enabled) {
                flowOf(BubbleGate(enabled = false, canDrawOverlays = false))
            } else {
                permissionUpdates(overlayPermission::canDrawOverlays, PERMISSION_POLL_MS)
                    .map { granted -> BubbleGate(enabled = true, canDrawOverlays = granted) }
            }
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
                if (disconnectedBeforeRehydration) return@launch

                _currentState.value = restored
                activeSessionId = result.sessionId
                observeDayTotals(result.sessionId)
                if (restored is ShiftState.OnTrip) {
                    activeTripStartMs = restored.tripStartedAtMs
                    activeTripId = result.tripId
                }
                controller.publishState(restored)
                if (!startInForeground(restored)) return@launch
                // Connected time keeps counting: an interval may be missing (migrated open session).
                intervalLock.withLock {
                    repository.openConnectedInterval(result.sessionId, startedAt = clock.nowMs())
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Rehydration failed; staying Disconnected", t)
            } finally {
                rehydrated = true
                rehydration.complete(Unit)
                stopIfIdle()
            }
        }
    }

    /** Releases an idle service (and its foreground notification) once nothing is left to do. */
    private fun stopIfIdle() {
        val stop = ServiceStartPolicy.shouldStopWhenIdle(
            state = _currentState.value,
            rehydrated = rehydrated,
            connectPending = connectPending,
            startCommandSeen = startCommandSeen
        )
        if (stop) {
            stopObservingTotals()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startCommandSeen = true
        if (intent == null) {
            // System restart after the process was killed: rehydration decides whether there is
            // anything to run. An active shift (even mid-ride) is restored and goes back to the
            // foreground; with no active session the idle policy stops the service. Sticky, so a
            // later kill of the restored shift is recovered too.
            stopIfIdle()
            return START_STICKY
        }
        // Delivered via startForegroundService / getForegroundService: satisfy startForeground
        // up front so no early-return path below can leave the requirement unmet.
        if (!startInForeground(_currentState.value)) return START_NOT_STICKY
        when (intent.action) {
            ShiftServiceController.ACTION_CONNECT -> handleConnect()
            ShiftServiceController.ACTION_DISCONNECT -> handleDisconnect()
            ShiftServiceController.ACTION_START_TRIP -> handleStartTrip()
            ShiftServiceController.ACTION_FINISH_TRIP -> handleFinishTrip(intent)
            ShiftServiceController.ACTION_CANCEL_TRIP -> handleCancelTrip(intent)
            // Rehydration (started in onCreate) does the work; the idle policy stops the service if empty.
            ShiftServiceController.ACTION_RESTORE -> stopIfIdle()
            ShiftServiceController.ACTION_END_SHIFT -> handleEndShift(ShiftServiceController.deductionAmountsFrom(intent))
            else -> stopIfIdle()
        }
        return START_STICKY
    }

    private fun handleConnect() {
        if (_currentState.value !is ShiftState.Disconnected) return
        connectPending = true
        scope.launch {
            try {
                val now = clock.nowMs()
                // A paused shift leaves its session open in Room while this service instance is gone:
                // resume it instead of opening a second one.
                val sessionId = activeSessionId
                    ?: repository.activeSession()?.id
                    ?: startShift(startedAt = now)
                activeSessionId = sessionId
                // Idempotent: an interval left open (service restarted while connected) is kept.
                intervalLock.withLock { repository.openConnectedInterval(sessionId, startedAt = now) }
                observeDayTotals(sessionId)
                val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.Connect)
                _currentState.value = next
                controller.publishState(next)
                startInForeground(next)
            } finally {
                connectPending = false
                stopIfIdle()
            }
        }
    }

    private fun handleDisconnect() {
        if (_currentState.value is ShiftState.Disconnected) {
            // Fresh instance whose rehydration may still be in flight: the shift can be open in Room.
            if (!rehydrated) disconnectedBeforeRehydration = true
            val now = clock.nowMs()
            scope.launch {
                try {
                    val sessionId = activeSessionId ?: repository.activeSession()?.id
                    if (sessionId != null) {
                        withContext(NonCancellable) {
                            intervalLock.withLock { repository.closeConnectedInterval(sessionId, endedAt = now) }
                        }
                    }
                } finally {
                    stopIfIdle()
                }
            }
            return
        }
        if (_currentState.value !is ShiftState.Connected) {
            stopIfIdle()
            return
        }
        val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.Disconnect)
        _currentState.value = next
        controller.publishState(next)
        stopObservingTotals()
        // Keep the service alive but remove the foreground notification.
        stopForeground(STOP_FOREGROUND_REMOVE)
        val sessionId = activeSessionId
        val now = clock.nowMs()
        scope.launch {
            try {
                // Finish the write before the service can be destroyed (which cancels the scope).
                if (sessionId != null) {
                    withContext(NonCancellable) {
                        intervalLock.withLock { repository.closeConnectedInterval(sessionId, endedAt = now) }
                    }
                }
            } finally {
                // The service is no longer needed when disconnected; let it die so
                // we don't hold an empty notification slot. The controller's state
                // survives process scope (singleton), so the UI remains accurate. A
                // reconnect that arrived meanwhile keeps it alive (connectPending).
                stopIfIdle()
            }
        }
    }

    /**
     * Starts a ride. Single-flight: while one start is being persisted a second one (a tap on the
     * notification and one on the bubble) is ignored, so two calls can never race over the same
     * open row. That is also what makes the failure cleanup below safe: when it runs, no other
     * start of this service can own an open row, and a live ride means the state is OnTrip.
     */
    private fun handleStartTrip() {
        if (_currentState.value !is ShiftState.Connected || startInFlight) {
            stopIfIdle()
            return
        }
        startInFlight = true
        val now = clock.nowMs()
        scope.launch {
            try {
                persistAndEnterTrip(now)
            } finally {
                startInFlight = false
            }
        }
    }

    private suspend fun persistAndEnterTrip(now: Long) {
        val sessionId = activeSessionId ?: return
        // The ride is persisted first: if that fails there is no ride, not one that only lives in memory.
        val started = try {
            startTrip(sessionId, startedAt = now)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Log.e(TAG, "Persisting the new trip failed; the shift stays Connected", t)
            // The row may have been committed before the failure; no ride exists, so none may stay.
            discardOpenTrip(sessionId, survivesCancellation = true)
            controller.publishError(ShiftError.StartTripFailed)
            return
        }
        val current = _currentState.value
        if (current !is ShiftState.Connected) {
            // The shift was paused meanwhile: the row must not stay behind as an orphan.
            discardOpenTrip(sessionId, survivesCancellation = true)
            return
        }
        val next = stateMachine.transition(
            current,
            ShiftStateMachine.Action.StartTrip(tripStartedAtMs = started.startedAt)
        )
        _currentState.value = next
        activeTripStartMs = started.startedAt
        activeTripId = started.tripId
        controller.publishState(next)
        updateNotification(next)
    }

    private fun handleFinishTrip(intent: Intent) {
        val tripId = activeTripId
        val tripStart = activeTripStartMs
        if (_currentState.value !is ShiftState.OnTrip || tripId == null || tripStart == null) {
            stopIfIdle()
            return
        }

        val platformId = ShiftServiceController.platformIdFrom(intent)
        val amountMinor = intent.getLongExtra(ShiftServiceController.EXTRA_AMOUNT_MINOR, 0L)
        val paymentName = intent.getStringExtra(ShiftServiceController.EXTRA_PAYMENT_METHOD)

        val now = clock.nowMs()
        scope.launch {
            val outcome = finishTripHandler.handle(
                tripId = tripId,
                tripStartedAt = tripStart,
                endedAt = now,
                platformId = platformId,
                amountMinor = amountMinor,
                paymentName = paymentName
            )
            val result = finishTripReducer.reduce(_currentState.value, outcome)
            if (result.error != null) {
                // Stay in OnTrip so the user can retry; tell the UI why.
                controller.publishError(result.error)
                return@launch
            }
            val next = result.state
            _currentState.value = next
            activeTripStartMs = null
            activeTripId = null
            controller.publishState(next)
            updateNotification(next)
        }
    }

    /**
     * Drops the open ride row of [sessionId] (it has no amount, so it is discarded like a cancel).
     * Best effort: a failure is logged and never blocks the caller.
     *
     * [survivesCancellation] picks how the service being destroyed meanwhile is handled. Orphan
     * cleanup after a start (true) must finish even then, or the row would be restored as a ghost
     * ride. Ending the shift (false) is itself cancellable work, so cancellation just propagates.
     */
    private suspend fun discardOpenTrip(sessionId: Long, survivesCancellation: Boolean) {
        try {
            if (survivesCancellation) withContext(NonCancellable) { cancelTrip(sessionId) } else cancelTrip(sessionId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            // An orphan row is never counted; restoring it as a ghost ride is the only harm left.
            Log.e(TAG, "Discarding the open ride row failed", t)
        }
    }

    private fun handleCancelTrip(intent: Intent) {
        val tripStart = ShiftServiceController.tripStartedAtFrom(intent)
        if (tripStart == null || (rehydrated && !isOnTrip(tripStart))) {
            stopIfIdle()
            return
        }
        scope.launch {
            // A fresh instance may still be restoring the ride from Room: wait for that before judging.
            rehydration.await()
            val sessionId = activeSessionId
            if (sessionId == null || !isOnTrip(tripStart)) {
                stopIfIdle()
                return@launch
            }
            try {
                // Deletes the persisted open row, so the discarded ride is not restored after a restart.
                cancelTrip(sessionId)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                Log.e(TAG, "Cancelling the trip failed; the ride stays active", t)
                return@launch
            }
            // A finish (or a newer ride) may have won the race while the delete was in flight.
            if (!isOnTrip(tripStart)) return@launch
            val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.CancelTrip)
            _currentState.value = next
            activeTripStartMs = null
            activeTripId = null
            controller.publishState(next)
            updateNotification(next)
        }
    }

    /** True while the shift is on the ride that started at [tripStartedAtMs]. */
    private fun isOnTrip(tripStartedAtMs: Long?): Boolean {
        val state = _currentState.value
        return tripStartedAtMs != null && state is ShiftState.OnTrip && state.tripStartedAtMs == tripStartedAtMs
    }

    /** [amounts] (deduction id to minor units) are stored with the shift before its summary is built. */
    private fun handleEndShift(amounts: Map<Long, Long>) {
        scope.launch {
            // After a pause this instance may be fresh: fall back to the open session in Room.
            val sessionId = activeSessionId ?: repository.activeSession()?.id
            if (sessionId == null) {
                stopObservingTotals()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            val now = clock.nowMs()
            discardOpenTrip(sessionId, survivesCancellation = false)
            endShift(sessionId, endedAt = now)
            saveExpenses(sessionId, amounts)
            val summary = getDailySummary(sessionId)
            controller.publishSummary(summary)
            val next = stateMachine.transition(_currentState.value, ShiftStateMachine.Action.EndShift)
            _currentState.value = next
            activeSessionId = null
            activeTripStartMs = null
            activeTripId = null
            controller.publishState(next)
            stopObservingTotals()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Best effort: losing the expenses must never keep the shift from ending. */
    private suspend fun saveExpenses(sessionId: Long, amounts: Map<Long, Long>) {
        if (amounts.isEmpty()) return
        try {
            val result = deductions.saveSessionAmounts(sessionId, amounts)
            if (result != DeductionResult.Ok) {
                Log.e(TAG, "Shift expenses were refused ($result); ending the shift without them")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            Log.e(TAG, "Saving the shift expenses failed; ending the shift without them", t)
        }
    }

    /**
     * Enters the foreground. Only the platform call through [foregroundStarter] is guarded: the system may refuse (`ForegroundServiceStartNotAllowedException` on
     * API 31+, a subclass of [IllegalStateException]; [SecurityException] for a missing type
     * permission), typically on a sticky restart while the app is in the background. The class is
     * never named here because it does not exist on minSdk 29.
     *
     * A refusal does not crash and loses nothing: the shift and any open ride are already in Room and
     * come back when the app opens. The driver is told with a plain notification and the service stops.
     *
     * @return true when the service is in the foreground, false when it was refused and is stopping.
     */
    private fun startInForeground(state: ShiftState): Boolean {
        // Built outside the try: a bug here is ours, deterministic, and must surface instead of
        // being mistaken for the system saying no.
        val notification = notificationFactory.build(state, dayTotals)
        try {
            foregroundStarter.start(this, NotificationFactory.NOTIF_ID, notification)
        } catch (refused: RuntimeException) {
            Log.e(TAG, "The system refused startForeground (sdk ${Build.VERSION.SDK_INT}); the shift stays saved", refused)
            postShiftSavedNotice()
            resetAfterRefusedForeground()
            stopSelf()
            return false
        }
        cancelShiftSavedNotice()
        return true
    }

    /**
     * This instance is about to stop, so nothing it published may outlive it: the singleton
     * controller goes back to Disconnected, which lets the next app launch ask for a restore. The
     * shift and any open ride are untouched in Room.
     */
    private fun resetAfterRefusedForeground() {
        stopObservingTotals()
        _currentState.value = ShiftState.Disconnected
        activeSessionId = null
        activeTripStartMs = null
        activeTripId = null
        controller.publishState(ShiftState.Disconnected)
    }

    private fun postShiftSavedNotice() {
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        // areNotificationsEnabled is false when POST_NOTIFICATIONS is not granted on API 33+.
        if (!nm.areNotificationsEnabled()) return
        try {
            nm.notify(NotificationFactory.NOTIF_ID_SHIFT_SAVED, notificationFactory.buildShiftSaved())
        } catch (t: RuntimeException) {
            Log.e(TAG, "Could not post the shift-saved notice", t)
        }
    }

    private fun cancelShiftSavedNotice() {
        androidx.core.app.NotificationManagerCompat.from(this).cancel(NotificationFactory.NOTIF_ID_SHIFT_SAVED)
    }

    private fun updateNotification(state: ShiftState) {
        val notification = notificationFactory.build(state, dayTotals)
        val nm = androidx.core.app.NotificationManagerCompat.from(this)
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(NotificationFactory.NOTIF_ID, notification)
            } catch (security: SecurityException) {
                // POST_NOTIFICATIONS not granted on API 33+; service still runs.
            }
        }
    }

    /**
     * Keeps [dayTotals] current and re-posts the notification when a trip is
     * saved. The chronometer is system-driven, so no per-second refresh is needed.
     */
    private fun observeDayTotals(sessionId: Long) {
        intervalsJob?.cancel()
        intervalsJob = scope.launch {
            repository.observeConnectedIntervals(sessionId).collect { connectedIntervals.value = it }
        }
        totalsJob?.cancel()
        totalsJob = scope.launch {
            repository.observeTrips(sessionId).collect { trips ->
                dayTotals = DayTotals.from(trips)
                val state = _currentState.value
                if (state !is ShiftState.Disconnected) updateNotification(state)
            }
        }
    }

    private fun stopObservingTotals() {
        totalsJob?.cancel()
        totalsJob = null
        intervalsJob?.cancel()
        intervalsJob = null
        connectedIntervals.value = emptyList()
        dayTotals = DayTotals.EMPTY
    }

    override fun onDestroy() {
        bubbleOverlay.hide()
        scope.cancel()
        super.onDestroy()
    }

    // Exposed for testing only — package-private accessor.
    internal fun _stateForTest(): StateFlow<ShiftState> = currentState

    private companion object {
        private const val TAG = "ShiftService"
        private const val PERMISSION_POLL_MS = 2_000L
    }
}
