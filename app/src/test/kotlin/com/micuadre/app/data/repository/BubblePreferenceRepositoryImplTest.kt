package com.micuadre.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/** The real repository over a real Preferences DataStore backed by a temp file. */
class BubblePreferenceRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(Job() + Dispatchers.IO)
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: BubblePreferenceRepositoryImpl

    @Before
    fun setUp() {
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.newFile("settings.preferences_pb").also { it.delete() }
        }
        repository = BubblePreferenceRepositoryImpl(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `defaults to off when nothing is stored`() = runBlocking {
        assertEquals(false, repository.bubbleEnabled.first())
    }

    @Test
    fun `persists both values`() = runBlocking {
        repository.setBubbleEnabled(true)
        assertEquals(true, repository.bubbleEnabled.first())
        repository.setBubbleEnabled(false)
        assertEquals(false, repository.bubbleEnabled.first())
    }

    @Test
    fun `value survives a new repository over the same store`() = runBlocking {
        repository.setBubbleEnabled(true)
        assertEquals(true, BubblePreferenceRepositoryImpl(dataStore).bubbleEnabled.first())
    }

    @Test
    fun `a read IO error falls back to off`() = runBlocking {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IOException("corrupt") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw IOException("unused")
        }
        assertEquals(false, BubblePreferenceRepositoryImpl(failing).bubbleEnabled.first())
    }

    @Test
    fun `non IO read errors are not swallowed`() {
        val failing = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("bug") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw IOException("unused")
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { BubblePreferenceRepositoryImpl(failing).bubbleEnabled.first() }
        }
    }
}
