package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
            repository.recordFinishedTrip(
                session, didi, T0 + 10 * MINUTE, T0 + 30 * MINUTE, 10_000L, PaymentMethod.CASH
            )
        )
        assertTrue(
            repository.recordFinishedTrip(
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
    fun `summary of an open session has no end and does not count an unfinished trip`() = runBlocking {
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
        assertEquals(0, summary.tripCount)
        assertTrue(summary.trips.isEmpty())
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

        assertTrue(repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))

        val trip = repository.observeTrips(session).first().single()
        assertEquals(didi, trip.platformId)
        assertEquals("Didi", trip.platformName)
        assertEquals(didi, repository.lastFinishedTrip()!!.platformId)
    }

    @Test
    fun `finishing a trip on an unknown platform is rejected`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        assertFalse(repository.recordFinishedTrip(session, 99L, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))

        assertEquals(0, repository.summary(session).tripCount)
    }

    @Test
    fun `finishing a trip on a deleted platform is rejected`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val gone = addPlatform("Gone", deletedAt = T0)

        assertFalse(repository.recordFinishedTrip(session, gone, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))

        assertEquals(0, repository.summary(session).tripCount)
    }

    @Test
    fun `a hidden but not deleted platform still accepts trips`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val hidden = addPlatform("Hidden", visible = false)

        assertTrue(repository.recordFinishedTrip(session, hidden, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH))
    }

    @Test
    fun `invalid amount or time range is rejected`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")

        assertFalse(repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 0L, PaymentMethod.CASH))
        assertFalse(repository.recordFinishedTrip(session, didi, T0 + MINUTE, T0, 100L, PaymentMethod.CASH))
    }

    @Test
    fun `a deleted platform keeps its name on past trips`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)

        db.openHelper.writableDatabase.execSQL("UPDATE platforms SET deletedAt = $T0 WHERE id = $didi")

        assertEquals("Didi", repository.summary(session).trips.single().platformName)
        assertEquals("Didi", repository.observeTrips(session).first().single().platformName)
        assertEquals("Didi", repository.lastFinishedTrip()!!.platformName)
    }

    @Test
    fun `session activity trips carry the platform name`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)

        val activity = repository.observeSessionActivity(T0, T0 + HOUR).first()

        assertEquals(listOf("Didi"), activity.single().trips.map { it.platformName })
    }

    @Test
    fun `cancelling deletes only the in-progress trip of that session`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val other = repository.createSession(startedAt = T0 + HOUR)
        val didi = addPlatform("Didi")
        repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)
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
        repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)

        repository.cancelInProgressTrip(session)

        assertEquals(1, repository.observeTrips(session).first().size)
        assertEquals(7_000L, repository.totalMinor(session))
    }

    @Test
    fun `openTrip persists the start and is idempotent per session`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        val id = repository.openTrip(session, T0 + MINUTE)
        val again = repository.openTrip(session, T0 + 2 * MINUTE)

        assertEquals(id, again)
        val open = repository.inProgressTrip(session)!!
        assertEquals(id, open.id)
        assertEquals(T0 + MINUTE, open.startedAt)
        assertNull(open.endedAt)
        assertNull(open.amountMinor)
        assertNull(open.paymentMethod)
    }

    @Test
    fun `an open trip maps to a domain trip without a platform`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.openTrip(session, T0)

        val open = repository.inProgressTrip(session)!!

        assertEquals(0L, open.platformId)
        assertEquals("", open.platformName)
    }

    @Test
    fun `finishOpenTrip closes the same row and keeps its start time`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        val id = repository.openTrip(session, T0)

        assertTrue(repository.finishOpenTrip(id, didi, T0 + 5 * MINUTE, 9_000L, PaymentMethod.TRANSFER))

        assertNull(repository.inProgressTrip(session))
        val trip = repository.observeTrips(session).first().single()
        assertEquals(listOf(id, didi, T0, T0 + 5 * MINUTE, 9_000L), listOf(trip.id, trip.platformId, trip.startedAt, trip.endedAt, trip.amountMinor))
        assertEquals("Didi", trip.platformName)
        assertEquals(PaymentMethod.TRANSFER, trip.paymentMethod)
        assertEquals(9_000L, repository.totalMinor(session))
    }

    @Test
    fun `finishOpenTrip rejects invalid input and keeps the trip open`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        val deleted = addPlatform("Old", deletedAt = T0)
        val id = repository.openTrip(session, T0)

        assertFalse(repository.finishOpenTrip(id, didi, T0 + MINUTE, 0L, PaymentMethod.CASH))
        assertFalse(repository.finishOpenTrip(id, didi, T0, 100L, PaymentMethod.CASH))
        assertFalse(repository.finishOpenTrip(id, deleted, T0 + MINUTE, 100L, PaymentMethod.CASH))
        assertFalse(repository.finishOpenTrip(id, 999L, T0 + MINUTE, 100L, PaymentMethod.CASH))

        assertEquals(id, repository.inProgressTrip(session)!!.id)
    }

    @Test
    fun `cancelling after openTrip deletes the persisted row`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.openTrip(session, T0)

        repository.cancelInProgressTrip(session)

        assertNull(repository.inProgressTrip(session))
    }

    @Test
    fun `an open trip never reaches a list, summary, total or session activity`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)
        repository.openTrip(session, T0 + 2 * MINUTE)

        assertEquals(listOf(7_000L), repository.observeTrips(session).first().map { it.amountMinor })
        assertEquals(7_000L, repository.totalMinor(session))
        val summary = repository.summary(session)
        assertEquals(1, summary.tripCount)
        assertEquals(7_000L, summary.totalAmountMinor)
        assertEquals(MINUTE, summary.totalDurationMs)
        assertEquals(1, summary.trips.size)
        assertEquals(
            listOf(7_000L),
            repository.observeSessionActivity(T0, T0 + HOUR).first().single().trips.map { it.amountMinor }
        )
        assertEquals(7_000L, repository.lastFinishedTrip()!!.amountMinor)
    }

    @Test
    fun `a session with only an open trip has an empty summary and activity`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        repository.openTrip(session, T0)

        val summary = repository.summary(session)
        assertEquals(0, summary.tripCount)
        assertEquals(0L, summary.totalAmountMinor)
        assertEquals(0L, summary.avgDurationMs)
        assertTrue(summary.trips.isEmpty())
        assertTrue(repository.observeSessionActivity(T0, T0 + HOUR).first().single().trips.isEmpty())
        assertNull(repository.lastFinishedTrip())
    }

    @Test
    fun `a closed row without a platform is dropped instead of reaching closed-trip code`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                "VALUES ($session, NULL, $T0, ${T0 + MINUTE}, 100, 'CASH')"
        )

        assertTrue(repository.observeTrips(session).first().isEmpty())
        assertTrue(repository.summary(session).trips.isEmpty())
        assertTrue(repository.observeSessionActivity(T0, T0 + HOUR).first().single().trips.isEmpty())
        assertNull(repository.lastFinishedTrip())
    }

    @Test
    fun `inserting an open domain trip with no platform stores a null platform`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        repository.insertTrip(inProgress(session, platformId = 0L, startedAt = T0))

        assertEquals(T0, repository.inProgressTrip(session)!!.startedAt)
    }

    @Test
    fun `inserting a closed trip without a platform is rejected and nothing is stored`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val closedWithoutPlatform = inProgress(session, platformId = 0L, startedAt = T0)
            .copy(endedAt = T0 + MINUTE, amountMinor = 700L, paymentMethod = PaymentMethod.CASH)

        try {
            repository.insertTrip(closedWithoutPlatform)
            fail("expected the closed trip without a platform to be rejected")
        } catch (expected: IllegalArgumentException) {
            // rejected before reaching storage
        }

        assertEquals(0L, repository.totalMinor(session))
        assertEquals(0, db.tripDao().countBySession(session))
        assertTrue(repository.observeTrips(session).first().isEmpty())
    }

    @Test
    fun `a stray closed row without a platform is not counted by the total or the summary`() = runBlocking {
        val session = repository.createSession(startedAt = T0)
        val didi = addPlatform("Didi")
        repository.recordFinishedTrip(session, didi, T0, T0 + MINUTE, 7_000L, PaymentMethod.CASH)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                "VALUES ($session, NULL, $T0, ${T0 + MINUTE}, 700, 'CASH')"
        )

        assertEquals(7_000L, repository.totalMinor(session))
        assertEquals(1, db.tripDao().countBySession(session))
        val summary = repository.summary(session)
        assertEquals(7_000L, summary.totalAmountMinor)
        assertEquals(1, summary.tripCount)
        assertEquals(1, summary.trips.size)
    }

    @Test
    fun `openTrip racing with cancel never throws`() = runBlocking {
        val session = repository.createSession(startedAt = T0)

        coroutineScope {
            repeat(200) { i ->
                launch(Dispatchers.Default) { repository.openTrip(session, T0 + i) }
                launch(Dispatchers.Default) { repository.cancelInProgressTrip(session) }
            }
        }

        assertEquals(0, db.tripDao().countBySession(session))
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
