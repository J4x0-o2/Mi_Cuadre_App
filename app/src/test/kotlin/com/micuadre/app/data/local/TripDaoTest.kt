package com.micuadre.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.dao.TripDao
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.data.local.entity.SessionEntity
import com.micuadre.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [TripDao] over an in-memory Room database: open trips are persisted but never counted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripDaoTest {

    private lateinit var db: MiCuadreDatabase
    private lateinit var dao: TripDao
    private var session = 0L
    private var didi = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.tripDao()
        runBlocking {
            session = db.sessionDao().insert(SessionEntity(startedAt = T0, endedAt = null))
            didi = db.platformDao().insert(
                PlatformEntity(name = "Didi", visible = true, createdAt = T0, deletedAt = null)
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun closedTrip(amount: Long, startedAt: Long = T0): Long = dao.insert(
        TripEntity(
            sessionId = session, platformId = didi, startedAt = startedAt, endedAt = startedAt + MINUTE,
            amountMinor = amount, paymentMethod = "CASH"
        )
    )

    @Test
    fun `insertOpenIfNone writes a row with only the start time`() = runBlocking {
        val id = dao.insertOpenIfNone(session, T0 + MINUTE)

        val row = dao.inProgressBySession(session)!!
        assertEquals(id, row.id)
        assertEquals(T0 + MINUTE, row.startedAt)
        assertNull(row.endedAt)
        assertNull(row.amountMinor)
        assertNull(row.paymentMethod)
        assertNull(row.platformId)
    }

    @Test
    fun `insertOpenIfNone does not add a second open trip to the same session`() = runBlocking {
        val first = dao.insertOpenIfNone(session, T0)

        assertEquals(-1L, dao.insertOpenIfNone(session, T0 + MINUTE))

        assertEquals(first, dao.inProgressBySession(session)!!.id)
        assertEquals(T0, dao.inProgressBySession(session)!!.startedAt)
    }

    @Test
    fun `openIfNone inserts the first open trip and returns the existing one afterwards`() = runBlocking {
        val first = dao.openIfNone(session, T0)
        val again = dao.openIfNone(session, T0 + MINUTE)

        assertEquals(first, again)
        assertEquals(T0, dao.inProgressBySession(session)!!.startedAt)
        assertEquals(1, db.openHelper.writableDatabase
            .query("SELECT COUNT(*) FROM trips WHERE endedAt IS NULL").use { it.moveToFirst(); it.getInt(0) })
    }

    @Test
    fun `a closed row without a platform is excluded from lists, total and count alike`() = runBlocking {
        closedTrip(5_000L)
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                "VALUES ($session, NULL, $T0, ${T0 + MINUTE}, 700, 'CASH')"
        )

        assertEquals(listOf<Long?>(5_000L), dao.listBySession(session).map { it.amountMinor })
        assertEquals(listOf<Long?>(5_000L), dao.observeBySession(session).first().map { it.amountMinor })
        assertEquals(5_000L, dao.sumAmountMinor(session))
        assertEquals(1, dao.countBySession(session))
    }

    @Test
    fun `finishOpen closes the row in place with platform, amount and payment`() = runBlocking {
        val id = dao.insertOpenIfNone(session, T0)

        val changed = dao.finishOpen(id, didi, T0 + 5 * MINUTE, 9_000L, "TRANSFER")

        assertEquals(1, changed)
        assertNull(dao.inProgressBySession(session))
        val row = dao.listBySession(session).single()
        assertEquals(id, row.id)
        assertEquals(
            listOf<Long?>(didi, T0, T0 + 5 * MINUTE, 9_000L),
            listOf(row.platformId, row.startedAt, row.endedAt, row.amountMinor)
        )
        assertEquals("TRANSFER", row.paymentMethod)
    }

    @Test
    fun `finishOpen refuses an unknown or deleted platform and leaves the trip open`() = runBlocking {
        val id = dao.insertOpenIfNone(session, T0)
        val gone = db.platformDao().insert(
            PlatformEntity(name = "Gone", visible = true, createdAt = T0, deletedAt = T0)
        )

        assertEquals(0, dao.finishOpen(id, 999L, T0 + MINUTE, 100L, "CASH"))
        assertEquals(0, dao.finishOpen(id, gone, T0 + MINUTE, 100L, "CASH"))

        assertNotNull(dao.inProgressBySession(session))
    }

    @Test
    fun `finishOpen refuses an end that is not after the start`() = runBlocking {
        val id = dao.insertOpenIfNone(session, T0)

        assertEquals(0, dao.finishOpen(id, didi, T0, 100L, "CASH"))
        assertEquals(0, dao.finishOpen(id, didi, T0 - 1, 100L, "CASH"))

        assertNotNull(dao.inProgressBySession(session))
    }

    @Test
    fun `finishOpen never rewrites a closed trip`() = runBlocking {
        val closed = closedTrip(5_000L)

        assertEquals(0, dao.finishOpen(closed, didi, T0 + 9 * MINUTE, 1L, "CASH"))

        assertEquals(5_000L, dao.listBySession(session).single().amountMinor)
    }

    @Test
    fun `deleteInProgressBySession removes the open row only`() = runBlocking {
        closedTrip(5_000L)
        dao.insertOpenIfNone(session, T0 + 2 * MINUTE)

        dao.deleteInProgressBySession(session)

        assertNull(dao.inProgressBySession(session))
        assertEquals(1, dao.countBySession(session))
    }

    @Test
    fun `an open trip is excluded from every list, total and count`() = runBlocking {
        closedTrip(5_000L)
        dao.insertOpenIfNone(session, T0 + 2 * MINUTE)

        assertEquals(listOf<Long?>(5_000L), dao.listBySession(session).map { it.amountMinor })
        assertEquals(listOf<Long?>(5_000L), dao.observeBySession(session).first().map { it.amountMinor })
        assertEquals(5_000L, dao.sumAmountMinor(session))
        assertEquals(1, dao.countBySession(session))
        assertEquals(5_000L, dao.lastFinished()!!.amountMinor)
    }

    @Test
    fun `a session with only an open trip has no trips, total or last finished trip`() = runBlocking {
        dao.insertOpenIfNone(session, T0)

        assertEquals(emptyList<TripEntity>(), dao.listBySession(session))
        assertEquals(emptyList<TripEntity>(), dao.observeBySession(session).first())
        assertEquals(0L, dao.sumAmountMinor(session))
        assertEquals(0, dao.countBySession(session))
        assertNull(dao.lastFinished())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val T0 = 1_767_254_400_000L
    }
}
