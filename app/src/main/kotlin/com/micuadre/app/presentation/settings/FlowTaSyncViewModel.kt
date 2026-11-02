package com.micuadre.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.R
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import com.micuadre.app.flowta.FlowTaAvailability
import com.micuadre.app.flowta.FlowTaClient
import com.micuadre.app.flowta.FlowTaLinkStatus
import com.micuadre.app.flowta.FlowTaSyncScheduler
import com.micuadre.app.presentation.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** What the "Enviar ingresos a FlowTa" row shows. */
data class FlowTaSyncState(
    val enabled: Boolean = false,
    /** Enabled, but FlowTa reports it no longer has a saved mapping for this app. */
    val showReconnect: Boolean = false,
    val showInstallDialog: Boolean = false
)

/** One-shot requests for the screen, which owns the Activity Result launcher. */
sealed interface FlowTaSyncEffect {
    /** Open FlowTa's connect screen and report back through [FlowTaSyncViewModel.onConnectResult]. */
    data object LaunchConnect : FlowTaSyncEffect
}

@HiltViewModel
class FlowTaSyncViewModel @Inject constructor(
    private val preferences: FlowTaSyncPreferenceRepository,
    private val availability: FlowTaAvailability,
    private val client: FlowTaClient,
    private val scheduler: FlowTaSyncScheduler
) : ViewModel() {

    private val linkStatus = MutableStateFlow(FlowTaLinkStatus.UNAVAILABLE)
    private val installDialog = MutableStateFlow(false)
    private val enabled = MutableStateFlow(false)

    val state: StateFlow<FlowTaSyncState> = combine(enabled, linkStatus, installDialog) { on, link, dialog ->
        FlowTaSyncState(
            enabled = on,
            showReconnect = on && link == FlowTaLinkStatus.NOT_LINKED,
            showInstallDialog = dialog
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, FlowTaSyncState())

    private val _effects = MutableSharedFlow<FlowTaSyncEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<FlowTaSyncEffect> = _effects.asSharedFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)

    /** One-shot message (e.g. a failed save); clear it with [onMessageShown]. */
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var messageSeq = 0L

    init {
        viewModelScope.launch {
            preferences.observe().distinctUntilChanged().collect { on ->
                enabled.value = on
                if (on) {
                    refreshLinkStatus()
                } else {
                    refreshJob?.cancel()
                    linkStatus.value = FlowTaLinkStatus.UNAVAILABLE
                }
            }
        }
    }

    fun onMessageShown(id: Long) {
        _message.update { if (it?.id == id) null else it }
    }

    /** The user may have just reconnected, reinstalled or cleared FlowTa; ask again. */
    fun onResume() {
        if (enabled.value) refreshLinkStatus()
    }

    fun onToggled(on: Boolean) {
        when {
            !on -> persist(false)
            !availability.isInstalled() -> installDialog.value = true
            else -> _effects.tryEmit(FlowTaSyncEffect.LaunchConnect)
        }
    }

    fun onReconnectClicked() {
        _effects.tryEmit(FlowTaSyncEffect.LaunchConnect)
    }

    /** Result of the connect screen: only an explicit OK enables (or keeps) the integration. */
    fun onConnectResult(connected: Boolean) {
        if (!connected) return
        persist(true)
        refreshJob?.cancel() // a pending answer from before the connect is stale
        linkStatus.value = FlowTaLinkStatus.LINKED
        // Rows queued while FlowTa was not linked can go out now.
        scheduler.schedule()
    }

    /** FlowTa's connect screen could not be opened. */
    fun onLaunchFailed() {
        _message.value = UiMessage(id = ++messageSeq, textRes = R.string.settings_flowta_launch_error)
    }

    fun onInstallDialogDismissed() {
        installDialog.value = false
    }

    private var refreshJob: Job? = null

    /** Asks FlowTa again; only the newest answer may update the state, so older ones are cancelled. */
    private fun refreshLinkStatus() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { linkStatus.value = client.linkStatus() }
    }

    private fun persist(on: Boolean) {
        viewModelScope.launch {
            try {
                preferences.setEnabled(on)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _message.value = UiMessage(id = ++messageSeq, textRes = R.string.settings_save_error)
            }
        }
    }
}
