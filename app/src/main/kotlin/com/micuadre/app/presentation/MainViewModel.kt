package com.micuadre.app.presentation

import android.content.Context
import android.os.Build
import android.os.SystemClock
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
    @ApplicationContext @Suppress("unused") private val app: Context
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private var tickerJob: Job? = null

    init {
        observeState()
        observeSummary()
    }

    private fun observeState() {
        viewModelScope.launch {
            observeShiftState().collect { state ->
                _ui.update { it.copy(shiftState = state) }
                when (state) {
                    is ShiftState.Connected -> {
                        startTicker(state.connectedSinceMs)
                        refreshTripsAndTotal()
                    }
                    is ShiftState.OnTrip -> {
                        startTicker(state.connectedSinceMs)
                        refreshTripsAndTotal()
                    }
                    is ShiftState.Disconnected -> {
                        stopTicker()
                        // Reset the visible list/total — the service will
                        // rehydrate them on next connect if needed.
                        _ui.update { it.copy(trips = emptyList(), totalMinor = 0L, elapsedSec = 0L) }
                    }
                }
            }
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
                val now = SystemClock.elapsedRealtime()
                val elapsed = ((now - connectedSinceMs).coerceAtLeast(0L)) / 1000L
                _ui.update { it.copy(elapsedSec = elapsed) }
                delay(1_000L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun refreshTripsAndTotal() {
        val session = repository.activeSession() ?: return
        repository.observeTrips(session.id).collect { trips ->
            val total = trips.sumOf { it.amountMinor ?: 0L }
            _ui.update { it.copy(trips = trips, totalMinor = total) }
        }
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
