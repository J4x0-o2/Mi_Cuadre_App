package com.micuadre.app.presentation

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.domain.usecase.EndShiftUseCase
import com.micuadre.app.domain.usecase.FinishTripUseCase
import com.micuadre.app.domain.usecase.GetDailySummaryUseCase
import com.micuadre.app.domain.usecase.ObserveShiftStateUseCase
import com.micuadre.app.domain.usecase.StartShiftUseCase
import com.micuadre.app.domain.usecase.StartTripUseCase
import com.micuadre.app.service.ShiftServiceController
import com.micuadre.app.util.Clock
import com.micuadre.app.util.TimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val controller: ShiftServiceController,
    @Suppress("unused") private val startShift: StartShiftUseCase,
    @Suppress("unused") private val endShift: EndShiftUseCase,
    @Suppress("unused") private val startTrip: StartTripUseCase,
    @Suppress("unused") private val finishTrip: FinishTripUseCase,
    private val observeShiftState: ObserveShiftStateUseCase,
    private val getDailySummary: GetDailySummaryUseCase,
    private val repository: ShiftRepository,
    private val clock: Clock,
    @ApplicationContext @Suppress("unused") private val app: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var tickerJob: Job? = null
    private var tickerSinceMs: Long? = null

    init {
        observeState()
        observeSummary()
    }

    private fun observeState() {
        viewModelScope.launch {
            homeSnapshots(
                shiftState = observeShiftState(),
                activeSessionId = { repository.activeSession()?.id },
                observeTrips = repository::observeTrips
            ).collect { snapshot ->
                _ui.update {
                    it.copy(
                        shiftState = snapshot.shiftState,
                        trips = snapshot.trips,
                        totalMinor = snapshot.totalMinor
                    )
                }
                syncTicker(snapshot.shiftState)
            }
        }
    }

    /** (Re)starts the elapsed-time ticker only when the shift start changes. */
    private fun syncTicker(state: ShiftState) {
        val since = when (state) {
            is ShiftState.Connected -> state.connectedSinceMs
            is ShiftState.OnTrip -> state.connectedSinceMs
            is ShiftState.Disconnected -> null
        }
        if (since == null) {
            stopTicker()
            tickerSinceMs = null
            _ui.update { it.copy(elapsedSec = 0L) }
        } else if (since != tickerSinceMs) {
            tickerSinceMs = since
            startTicker(since)
        }
    }

    private fun observeSummary() {
        viewModelScope.launch {
            controller.summaryEvents.collect { summary ->
                _ui.update { it.copy(summary = summary) }
            }
        }
    }

    private fun startTicker(connectedSinceMs: Long) {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                val elapsed = TimeUtils.elapsedSeconds(connectedSinceMs, clock.nowMs())
                _ui.update { it.copy(elapsedSec = elapsed) }
                delay(1_000L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun onConnectTapped() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            _ui.update { it.copy(needsNotificationPermission = true) }
        }
        controller.connect()
    }

    fun onNotificationPermissionConsumed() {
        _ui.update { it.copy(needsNotificationPermission = false) }
    }

    fun onDisconnectTapped() {
        controller.disconnect()
    }

    fun onNewTripTapped() {
        controller.startTrip()
    }

    fun onFinishTripTapped() {
        _ui.update { it.copy(showFinishSheet = true) }
    }

    fun onFinishTripDismissed() {
        _ui.update { it.copy(showFinishSheet = false, pendingError = null) }
    }

    fun onFinishTripConfirmed(
        platform: Platform,
        platformOtherLabel: String?,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ) {
        controller.finishTrip(platform, platformOtherLabel, amountMinor, paymentMethod)
        _ui.update { it.copy(showFinishSheet = false, pendingError = null) }
    }

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
