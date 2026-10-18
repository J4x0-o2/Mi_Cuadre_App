package com.micuadre.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.micuadre.app.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The real repository over a real Preferences DataStore backed by a temp file. */
class ThemePreferenceRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: ThemePreferenceRepositoryImpl

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("settings.preferences_pb")
                .also { it.delete() }
        }
        repository = ThemePreferenceRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun defaultsToSystemWhenNothingStored() = runBlocking {
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun persistsEachMode() = runBlocking {
        ThemeMode.entries.forEach { mode ->
            repository.setThemeMode(mode)
            assertEquals(mode, repository.themeMode.first())
        }
    }

    @Test
    fun unknownStoredValueFallsBackToSystem() = runBlocking {
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "PURPLE" }
        assertEquals(ThemeMode.SYSTEM, repository.themeMode.first())
    }

    @Test
    fun valueSurvivesANewRepositoryOverTheSameStore() = runBlocking {
        repository.setThemeMode(ThemeMode.SCHEDULED)
        assertEquals(ThemeMode.SCHEDULED, ThemePreferenceRepositoryImpl(dataStore).themeMode.first())
    }

    @Test
    fun readIoErrorFallsBackToSystem() = runBlocking {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw IOException("unused")
        }
        assertEquals(ThemeMode.SYSTEM, ThemePreferenceRepositoryImpl(failing).themeMode.first())
    }

    @Test
    fun nonIoReadErrorsAreNotSwallowed() {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("bug") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw IOException("unused")
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { ThemePreferenceRepositoryImpl(failing).themeMode.first() }
        }
    }
}
