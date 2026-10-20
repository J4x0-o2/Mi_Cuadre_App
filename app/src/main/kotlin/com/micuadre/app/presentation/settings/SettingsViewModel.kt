package com.micuadre.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.BuildConfig
import com.micuadre.app.R
import com.micuadre.app.domain.model.PlatformCounts
import com.micuadre.app.domain.model.ThemeMode
import com.micuadre.app.domain.repository.BubblePreferenceRepository
import com.micuadre.app.domain.repository.PlatformRepository
import com.micuadre.app.domain.repository.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.micuadre.app.overlay.OverlayPermission
import com.micuadre.app.presentation.UiMessage
import com.micuadre.app.util.AppVersion
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ThemePreferenceRepository,
    platforms: PlatformRepository,
    private val bubblePreferences: BubblePreferenceRepository,
    private val overlayPermission: OverlayPermission
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Visible-of-total platform counts for the Platforms row; null until the first load. */
    val platformCounts: StateFlow<PlatformCounts?> = platforms.observeActive()
        .map { PlatformCounts.of(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val permissionGranted = MutableStateFlow(overlayPermission.canDrawOverlays())

    /** Floating bubble toggle plus whether the overlay permission is granted. */
    val bubble: StateFlow<BubbleSettings> = combine(bubblePreferences.bubbleEnabled, permissionGranted) { enabled, granted ->
        BubbleSettings(enabled, granted)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BubbleSettings(enabled = false, permissionGranted = permissionGranted.value))

    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the screen should open the system "display over other apps" settings. */
    val permissionRequests: SharedFlow<Unit> = _permissionRequests.asSharedFlow()

    /** Installed version for the "About" section, e.g. "0.2.0" or "0.2.0 (debug)". */
    val versionLabel: String = AppVersion.label(BuildConfig.VERSION_NAME, BuildConfig.BUILD_TYPE)

    private val _message = MutableStateFlow<UiMessage?>(null)

    /** One-shot message (e.g. a failed save); clear it with [onMessageShown]. */
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var messageSeq = 0L

    fun onMessageShown(id: Long) {
        _message.update { if (it?.id == id) null else it }
    }

    /** Re-reads the system permission; the user may have just come back from its settings page. */
    fun onResume() {
        permissionGranted.value = overlayPermission.canDrawOverlays()
    }

    /** The permission is still missing and the user tapped the row again: send them back to the system page. */
    fun onBubblePermissionRetry() {
        onResume()
        if (!permissionGranted.value) _permissionRequests.tryEmit(Unit)
    }

    fun onBubbleToggled(enabled: Boolean) {
        viewModelScope.launch {
            try {
                bubblePreferences.setBubbleEnabled(enabled)
                onResume()
                if (enabled && !permissionGranted.value) _permissionRequests.tryEmit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _message.value = UiMessage(id = ++messageSeq, textRes = R.string.settings_save_error)
            }
        }
    }

    fun onSelect(mode: ThemeMode) {
        viewModelScope.launch {
            try {
                repository.setThemeMode(mode)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // The stored mode did not change, so themeMode keeps showing the previous one.
                _message.value = UiMessage(id = ++messageSeq, textRes = R.string.settings_save_error)
            }
        }
    }
}
