package com.micuadre.app.domain.repository

import com.micuadre.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface ThemePreferenceRepository {
    /** Emits the stored mode, [ThemeMode.SYSTEM] when nothing (valid) is stored. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
