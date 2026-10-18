package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real [ShiftRepositoryImpl] over an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftRepositoryImplTest {

    private lateinit var db: MiCuadreDatabase
    private lateinit var repository: ShiftRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ShiftRepositoryImpl(db.sessionDao(), db.tripDao(), db.connectedIntervalDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `opening an interval twice keeps a single open interval`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        repository.openConnectedInterval(session, startedAt = T0)
        repository.openConnectedInterval(session, startedAt = T0 + MINUTE)

        val intervals = repository.observeConnectedIntervals(session).first()
        assertEquals(listOf(T0 to null), intervals.map { it.startedAt to it.endedAt })
    }

    @Test
    fun `closing ends the open interval and a later open starts a new one`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        repository.openConnectedInterval(session, startedAt = T0)
        repository.closeConnectedInterval(session, endedAt = T0 + HOUR)
        repository.openConnectedInterval(session, startedAt = T0 + 2 * HOUR)

        val intervals = repository.observeConnectedIntervals(session).first()
        assertEquals(
            listOf<Pair<Long, Long?>>(T0 to T0 + HOUR, T0 + 2 * HOUR to null),
            intervals.map { it.startedAt to it.endedAt }
        )
    }

    @Test
    fun `closing with no open interval changes nothing`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.openConnectedInterval(session, startedAt = T0)
        repository.closeConnectedInterval(session, endedAt = T0 + HOUR)

        repository.closeConnectedInterval(session, endedAt = T0 + 5 * HOUR)

        assertEquals(T0 + HOUR, repository.observeConnectedIntervals(session).first().single().endedAt)
    }

    @Test
    fun `ending the session also closes its open interval at the end time`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.openConnectedInterval(session, startedAt = T0)

        repository.endSession(session, endedAt = T0 + HOUR)

        assertEquals(T0 + HOUR, repository.observeConnectedIntervals(session).first().single().endedAt)
        assertNull(repository.activeSession())
    }

    @Test
    fun `intervals of other sessions are not mixed in`() = runBlocking {
        val first = repository.createSession(startedAt = T0)
        val second = repository.createSession(startedAt = T0 + HOUR)
        repository.openConnectedInterval(first, startedAt = T0)
        repository.openConnectedInterval(second, startedAt = T0 + HOUR)

        assertEquals(listOf(T0), repository.observeConnectedIntervals(first).first().map { it.startedAt })
        assertEquals(listOf(T0 + HOUR), repository.observeConnectedIntervals(second).first().map { it.startedAt })
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        // 2026-01-01T08:00:00Z
        const val T0 = 1_767_254_400_000L
    }
}
