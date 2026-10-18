package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `summary maps the session boundaries, trips, totals and intervals`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.openConnectedInterval(session, startedAt = T0)
        repository.closeConnectedInterval(session, endedAt = T0 + HOUR)
        repository.openConnectedInterval(session, startedAt = T0 + 2 * HOUR)
        assertTrue(
            repository.finishTrip(
                session, Platform.DIDI, null, T0 + 10 * MINUTE, T0 + 30 * MINUTE, 10_000L, PaymentMethod.CASH
            )
        )
        assertTrue(
            repository.finishTrip(
                session, Platform.OTHER, " Uber ", T0 + 40 * MINUTE, T0 + 50 * MINUTE, 5_000L, PaymentMethod.TRANSFER
            )
        )
        repository.endSession(session, endedAt = T0 + 3 * HOUR)

        val summary = repository.summary(session)

        assertEquals(T0, summary.startedAt)
        assertEquals(T0 + 3 * HOUR, summary.endedAt)
        assertEquals(15_000L, summary.totalAmountMinor)
        assertEquals(2, summary.tripCount)
        assertEquals(30 * MINUTE, summary.totalDurationMs)
        assertEquals(15 * MINUTE, summary.avgDurationMs)
        assertEquals(
            listOf(Platform.OTHER to "Uber", Platform.DIDI to null),
            summary.trips.map { it.platform to it.platformOtherLabel }
        )
        assertEquals(
            listOf<Pair<Long, Long?>>(T0 to T0 + HOUR, T0 + 2 * HOUR to T0 + 3 * HOUR),
            summary.connectedIntervals.map { it.startedAt to it.endedAt }
        )
    }

    @Test
    fun `summary of an open session has no end and counts an unfinished trip as zero duration`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.insertTrip(
            Trip(
                sessionId = session, platform = Platform.INDRIVER, platformOtherLabel = null,
                startedAt = T0, endedAt = null, amountMinor = null, paymentMethod = null, id = 0L
            )
        )

        val summary = repository.summary(session)

        assertNull(summary.endedAt)
        assertEquals(1, summary.tripCount)
        assertEquals(0L, summary.totalAmountMinor)
        assertEquals(0L, summary.totalDurationMs)
    }

    @Test
    fun `summary of a session that does not exist has no start instead of epoch zero`() = runBlocking {
        val summary = repository.summary(sessionId = 999L)

        assertNull(summary.startedAt)
        assertNull(summary.endedAt)
        assertEquals(0, summary.tripCount)
        assertTrue(summary.connectedIntervals.isEmpty())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        // 2026-01-01T08:00:00Z
        const val T0 = 1_767_254_400_000L
    }
}
