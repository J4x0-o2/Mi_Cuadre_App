package com.micuadre.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface BubblePreferenceRepository {
    /** Emits whether the floating bubble is enabled; false when nothing is stored. */
    val bubbleEnabled: Flow<Boolean>

    suspend fun setBubbleEnabled(enabled: Boolean)
}
