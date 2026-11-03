package com.micuadre.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class FlowTaSyncPreferenceRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : FlowTaSyncPreferenceRepository {

    override fun observe(): Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY] ?: false }

    override suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY] = enabled }
    }

    private companion object {
        val KEY = booleanPreferencesKey("flowta_sync_enabled")
    }
}
