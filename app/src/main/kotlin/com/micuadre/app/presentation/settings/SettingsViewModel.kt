package com.micuadre.app.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.R
import com.micuadre.app.domain.model.ThemeMode
import com.micuadre.app.domain.repository.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.micuadre.app.presentation.UiMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: ThemePreferenceRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val _message = MutableStateFlow<UiMessage?>(null)

    /** One-shot message (e.g. a failed save); clear it with [onMessageShown]. */
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private var messageSeq = 0L

    fun onMessageShown(id: Long) {
        _message.update { if (it?.id == id) null else it }
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
