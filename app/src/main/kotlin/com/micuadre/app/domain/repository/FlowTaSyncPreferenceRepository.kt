package com.micuadre.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface FlowTaSyncPreferenceRepository {
    /** Emits whether finished trips are sent to FlowTa; false when nothing is stored. */
    fun observe(): Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)
}
