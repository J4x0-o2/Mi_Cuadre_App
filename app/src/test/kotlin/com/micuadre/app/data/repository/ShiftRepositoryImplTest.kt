package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        repository = ShiftRepositoryImpl(db.sessionDao(), db.tripDao(), db.connectedIntervalDao(), db.platformDao())
    }

    private suspend fun addPlatform(
        name: String,
        visible: Boolean = true,
        deletedAt: Long? = null
    ): Long = db.platformDao().insert(
        PlatformEntity(name = name, visible = visible, createdAt = T0, deletedAt = deletedAt)
    )

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
        val didi = addPlatform("Didi")
        val uber = addPlatform("Uber")
        repository.openConnectedInterval(session, startedAt = T0)
        repository.closeConnectedInterval(session, endedAt = T0 + HOUR)
        repository.openConnectedInterval(session, startedAt = T0 + 2 * HOUR)
        assertTrue(
            repository.finishTrip(
                session, didi, T0 + 10 * MINUTE, T0 + 30 * MINUTE, 10_000L, PaymentMethod.CASH
            )
        )
        assertTrue(
            repository.finishTrip(
                session, uber, T0 + 40 * MINUTE, T0 + 50 * MINUTE, 5_000L, PaymentMethod.TRANSFER
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
            listOf(uber to "Uber", didi to "Didi"),
            summary.trips.map { it.platformId to it.platformName }
        )
        assertEquals(
            listOf<Pair<Long, Long?>>(T0 to T0 + HOUR, T0 + 2 * HOUR to T0 + 3 * HOUR),
            summary.connectedIntervals.map { it.startedAt to it.endedAt }
        )
    }

    @Test
    fun `summary of an open session has no end and counts an unfinished trip as zero duration`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val indriver = addPlatform("InDriver")
        repository.insertTrip(
            Trip(
                sessionId = session, platformId = indriver, platformName = "InDriver",
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

    @Test
    fun `visible platforms exclude hidden and deleted ones and keep creation order`() = runBlocking {
        val first = addPlatform("InDriver")
        addPlatform("Hidden", visible = false)
        addPlatform("Gone", deletedAt = T0)
        val last = addPlatform("Beat")

        val visible = repository.observeVisiblePlatforms().first()

        assertEquals(listOf(first to "InDriver", last to "Beat"), visible.map { it.id to it.name })
    }

    @Test
    fun `finishing a trip stores it against the platform`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")

        assertTrue(repository.finishTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))

        val trip = repository.observeTrips(session).first().single()
        assertEquals(didi, trip.platformId)
        assertEquals("Didi", trip.platformName)
        assertEquals(didi, repository.lastFinishedTrip()!!.platformId)
    }

    @Test
    fun `finishing a trip on an unknown platform is rejected`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        assertFalse(repository.finishTrip(session, 99L, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))

        assertEquals(0, repository.summary(session).tripCount)
    }

    @Test
    fun `finishing a trip on a deleted platform is rejected`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val gone = addPlatform("Gone", deletedAt = T0)

        assertFalse(repository.finishTrip(session, gone, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))

        assertEquals(0, repository.summary(session).tripCount)
    }

    @Test
    fun `a hidden but not deleted platform still accepts trips`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val hidden = addPlatform("Hidden", visible = false)

        assertTrue(repository.finishTrip(session, hidden, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))
    }

    @Test
    fun `invalid amount or time range is rejected`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")

        assertFalse(repository.finishTrip(session, didi, T0, T0 + MINUTE, 0L, PaymentMethod.CASH))
        assertFalse(repository.finishTrip(session, didi, T0 + MINUTE, T0, 100L, PaymentMethod.CASH))
    }

    @Test
    fun `a deleted platform keeps its name on past trips`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.finishTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)

        db.openHelper.writableDatabase.execSQL("UPDATE platforms SET deletedAt = $T0 WHERE id = $didi")

        assertEquals("Didi", repository.summary(session).trips.single().platformName)
        assertEquals("Didi", repository.observeTrips(session).first().single().platformName)
        assertEquals("Didi", repository.lastFinishedTrip()!!.platformName)
    }

    @Test
    fun `session activity trips carry the platform name`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.finishTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)

        val activity = repository.observeSessionActivity(T0, T0 + HOUR).first()

        assertEquals(listOf("Didi"), activity.single().trips.map { it.platformName })
    }

    @Test
    fun `cancelling deletes only the in-progress trip of that session`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val other = repository.createSession(startedAt = T0 + HOUR)
        val didi = addPlatform("Didi")
        repository.finishTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)
        repository.insertTrip(inProgress(session, didi, T0 + 2 * MINUTE))
        repository.insertTrip(inProgress(other, didi, T0 + HOUR))

        repository.cancelInProgressTrip(session)

        assertNull(repository.inProgressTrip(session))
        assertEquals(1, repository.observeTrips(session).first().size)
        assertEquals(7_000L, repository.totalMinor(session))
        assertEquals(T0 + HOUR, repository.inProgressTrip(other)!!.startedAt)
    }

    @Test
    fun `cancelling with no in-progress trip changes nothing`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.finishTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)

        repository.cancelInProgressTrip(session)

        assertEquals(1, repository.observeTrips(session).first().size)
        assertEquals(7_000L, repository.totalMinor(session))
    }

    private fun inProgress(sessionId: Long, platformId: Long, startedAt: Long) = Trip(
        id = 0L,
        sessionId = sessionId,
        platformId = platformId,
        platformName = "",
        startedAt = startedAt,
        endedAt = null,
        amountMinor = null,
        paymentMethod = null
    )

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
        // 2026-01-01T08:00:00Z
        const val T0 = 1_767_254_400_000L
    }
}
