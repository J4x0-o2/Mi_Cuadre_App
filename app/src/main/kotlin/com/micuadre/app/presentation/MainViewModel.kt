package com.micuadre.app.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.R
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.domain.usecase.GetDailySummaryUseCase
import com.micuadre.app.domain.usecase.GetLastUsedPlatformUseCase
import com.micuadre.app.presentation.home.Elapsed
import com.micuadre.app.domain.usecase.ObservePeriodSummaryUseCase
import com.micuadre.app.domain.usecase.ObserveShiftStateUseCase
import com.micuadre.app.service.ShiftError
import com.micuadre.app.service.ShiftServiceController
import com.micuadre.app.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val controller: ShiftServiceController,
    private val observeShiftState: ObserveShiftStateUseCase,
    private val getDailySummary: GetDailySummaryUseCase,
    private val getLastUsedPlatform: GetLastUsedPlatformUseCase,
    private val repository: ShiftRepository,
    private val observePeriodSummary: ObservePeriodSummaryUseCase,
    private val clock: Clock,
    zone: ZoneId
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var tickerJob: Job? = null
    private var tickerKey: Pair<Long, Long?>? = null
    private var messageSeq = 0L
    private val finishSheets = FinishSheetCoordinator(
        ui = _ui,
        scope = viewModelScope,
        lastUsedPlatform = { getLastUsedPlatform() },
        nowMs = clock::nowMs
    )
    private val periodSummaries = PeriodSummaryCoordinator(
        ui = _ui,
        scope = viewModelScope,
        clock = clock,
        zone = zone,
        observe = observePeriodSummary::invoke
    )
    private val connectCoordinator = ConnectCoordinator(startShift = controller::connect)

    init {
        observeState()
        observeSummary()
        observeErrors()
    }

    private fun observeState() {
        viewModelScope.launch {
            homeSnapshots(
                shiftState = observeShiftState(),
                activeSessionId = { repository.activeSession()?.id },
                observeTrips = repository::observeTrips,
                observeIntervals = repository::observeConnectedIntervals
            ).collect { snapshot ->
                _ui.update {
                    it.copy(
                        shiftState = snapshot.shiftState,
                        trips = snapshot.trips,
                        totalMinor = snapshot.totalMinor,
                        hasOpenShift = snapshot.hasOpenShift,
                        connectedIntervals = snapshot.connectedIntervals
                    )
                }
                syncTicker(snapshot.shiftState)
                finishSheets.onShiftState(snapshot.shiftState)
            }
        }
    }

    /** (Re)starts the elapsed-time ticker only when the shift or trip start changes. */
    private fun syncTicker(state: ShiftState) {
        val key = when (state) {
            is ShiftState.Connected -> state.connectedSinceMs to null
            is ShiftState.OnTrip -> state.connectedSinceMs to state.tripStartedAtMs
            is ShiftState.Disconnected -> null
        }
        if (key == null) {
            stopTicker()
            tickerKey = null
            _ui.update { it.copy(elapsedSec = 0L, tripElapsedSec = 0L) }
        } else if (key != tickerKey) {
            tickerKey = key
            startTicker(state)
        }
    }

    private fun observeSummary() {
        viewModelScope.launch {
            controller.summaryEvents.collect { summary ->
                _ui.update { it.copy(summary = summary) }
            }
        }
    }

    private fun observeErrors() {
        viewModelScope.launch {
            controller.errorEvents.collect { error ->
                when (error) {
                    ShiftError.InvalidFinishTrip -> postMessage(R.string.error_finish_trip)
                }
            }
        }
    }

    fun onMessageShown(id: Long) {
        _ui.update { if (it.pendingMessage?.id == id) it.copy(pendingMessage = null) else it }
    }

    private fun postMessage(@StringRes textRes: Int) {
        val message = UiMessage(id = ++messageSeq, textRes = textRes)
        _ui.update { it.copy(pendingMessage = message) }
    }

    private fun startTicker(state: ShiftState) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val elapsed = Elapsed.of(state, clock.nowMs(), _ui.value.connectedIntervals)
                _ui.update { it.copy(elapsedSec = elapsed.shiftSec, tripElapsedSec = elapsed.tripSec) }
                delay(1_000L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun onConnectTapped(permission: NotificationPermission) {
        applyConnectEffect(connectCoordinator.onConnectTapped(permission))
    }

    /** Called with the outcome of the POST_NOTIFICATIONS prompt; starts the shift either way. */
    fun onNotificationPermissionResult(granted: Boolean) {
        _ui.update { it.copy(needsNotificationPermission = false) }
        applyConnectEffect(connectCoordinator.onPermissionResult(granted))
    }

    private fun applyConnectEffect(effect: ConnectCoordinator.Effect) {
        when (effect) {
            ConnectCoordinator.Effect.None -> Unit
            ConnectCoordinator.Effect.RequestPermission ->
                _ui.update { it.copy(needsNotificationPermission = true) }
            ConnectCoordinator.Effect.ShowDeniedHint ->
                postMessage(R.string.notif_permission_denied_hint)
        }
    }

    fun onDisconnectTapped() {
        controller.disconnect()
    }

    fun onNewTripTapped() {
        controller.startTrip()
    }

    /** Entry from outside the app (notification action). Consumed once; see [FinishSheetCoordinator]. */
    fun onLaunchRequest(request: LaunchRequest) = finishSheets.onLaunchRequest(request)

    fun onFinishTripTapped() = finishSheets.onFinishTapped()

    fun onFinishTripDismissed() {
        finishSheets.onDismissed()
        _ui.update { it.copy(finishSheet = null, pendingMessage = null) }
    }

    fun onFinishTripConfirmed(
        platform: Platform,
        platformOtherLabel: String?,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ) {
        finishSheets.onDismissed()
        controller.finishTrip(platform, platformOtherLabel, amountMinor, paymentMethod)
        _ui.update { it.copy(finishSheet = null, pendingMessage = null) }
    }

    fun onPeriodTapped(period: Period) = periodSummaries.onPeriodTapped(period)

    fun onPeriodSheetDismissed() = periodSummaries.onPeriodSheetDismissed()

    fun onEndShiftTapped() {
        controller.endShift()
    }

    fun onSummaryDismissed() {
        _ui.update { it.copy(summary = null) }
    }

    suspend fun fetchDailySummary(): DailySummary? {
        val session = repository.activeSession() ?: return null
        return getDailySummary(session.id)
    }

    override fun onCleared() {
        stopTicker()
        super.onCleared()
    }
}
