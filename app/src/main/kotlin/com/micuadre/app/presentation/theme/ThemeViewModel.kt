package com.micuadre.app.presentation.theme

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.micuadre.app.domain.repository.ThemePreferenceRepository
import com.micuadre.app.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import javax.inject.Inject

/**
 * Theme state for the whole activity. Null until the stored mode is read. The upstream stops
 * as soon as nobody collects (app in background) so the boundary timer is re-armed from a
 * fresh clock reading on every resume.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    repository: ThemePreferenceRepository,
    clock: Clock,
    @ApplicationContext context: Context
) : ViewModel() {

    val state: StateFlow<ThemeUiState?> =
        themeStates(repository.themeMode, clock, ZoneId::systemDefault, clockChanges(context))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), null)
}
