package com.micuadre.app.data.local

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity
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

/** The FlowTa income queue DAO over an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingFlowTaIncomeDaoTest {

    private lateinit var db: MiCuadreDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dao get() = db.pendingFlowTaIncomeDao()

    private fun row(tripId: Long, createdAt: Long, amountMinor: Long = 5000L) = PendingFlowTaIncomeEntity(
        tripId = tripId,
        amountMinor = amountMinor,
        paymentMethod = "CASH",
        endedAt = createdAt + 1_000L,
        platformName = "Didi",
        createdAt = createdAt
    )

    @Test
    fun `enqueue keeps a row already queued for the same trip untouched`() = runBlocking {
        dao.enqueue(row(1, 100L, amountMinor = 5000L))
        dao.markFailed(1, "timeout")

        dao.enqueue(row(1, 900L, amountMinor = 7500L))

        val only = dao.oldest(10).single()
        assertEquals(5000L, only.amountMinor)
        assertEquals(100L, only.createdAt)
        assertEquals(1, only.attempts)
        assertEquals("timeout", only.lastError)
        assertEquals(1, dao.count())
    }

    @Test
    fun `oldest breaks createdAt ties by trip id ascending`() = runBlocking {
        dao.enqueue(row(7, 100L))
        dao.enqueue(row(3, 100L))
        dao.enqueue(row(5, 100L))

        assertEquals(listOf(3L, 5L, 7L), dao.oldest(10).map { it.tripId })
    }

    @Test
    fun `markFailed on a missing row changes nothing`() = runBlocking {
        dao.enqueue(row(1, 100L))

        dao.markFailed(99, "boom")

        val only = dao.oldest(10).single()
        assertEquals(0, only.attempts)
        assertNull(only.lastError)
        assertEquals(1, dao.count())
    }

    @Test
    fun `oldest returns rows by createdAt ascending and honours the limit`() = runBlocking {
        dao.enqueue(row(1, 300L))
        dao.enqueue(row(2, 100L))
        dao.enqueue(row(3, 200L))

        assertEquals(listOf(2L, 3L, 1L), dao.oldest(10).map { it.tripId })
        assertEquals(listOf(2L, 3L), dao.oldest(2).map { it.tripId })
    }

    @Test
    fun `markFailed increments attempts and stores the last error`() = runBlocking {
        dao.enqueue(row(1, 100L))
        dao.enqueue(row(2, 200L))

        dao.markFailed(1, "timeout")
        dao.markFailed(1, "http 500")

        val first = dao.oldest(10).first { it.tripId == 1L }
        assertEquals(2, first.attempts)
        assertEquals("http 500", first.lastError)
        val untouched = dao.oldest(10).first { it.tripId == 2L }
        assertEquals(0, untouched.attempts)
        assertNull(untouched.lastError)
    }

    @Test
    fun `markFailed accepts a null error`() = runBlocking {
        dao.enqueue(row(1, 100L))
        dao.markFailed(1, "boom")
        dao.markFailed(1, null)

        val only = dao.oldest(10).single()
        assertEquals(2, only.attempts)
        assertNull(only.lastError)
    }

    @Test
    fun `delete removes only that trip`() = runBlocking {
        dao.enqueue(row(1, 100L))
        dao.enqueue(row(2, 200L))

        dao.delete(1)

        assertEquals(listOf(2L), dao.oldest(10).map { it.tripId })
    }

    @Test
    fun `count and observeCount follow the queue size`() = runBlocking {
        assertEquals(0, dao.count())
        assertEquals(0, dao.observeCount().first())

        dao.enqueue(row(1, 100L))
        dao.enqueue(row(2, 200L))

        assertEquals(2, dao.count())
        assertEquals(2, dao.observeCount().first())
    }
}
