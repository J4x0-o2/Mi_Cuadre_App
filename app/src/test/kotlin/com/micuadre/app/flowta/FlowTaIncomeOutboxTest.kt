package com.micuadre.app.flowta

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import com.micuadre.app.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FlowTaIncomeOutboxTest {

    private class FakePrefs(initial: Boolean) : FlowTaSyncPreferenceRepository {
        val state = MutableStateFlow(initial)
        override fun observe(): Flow<Boolean> = state
        override suspend fun setEnabled(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class FakeScheduler : FlowTaSyncScheduler {
        var calls = 0
        var failure: Throwable? = null
        override fun schedule() {
            calls++
            failure?.let { throw it }
        }
    }

    private lateinit var db: MiCuadreDatabase
    private val scheduler = FakeScheduler()
    private val clock = object : Clock {
        override fun nowMs() = 777L
    }
    private val names = mutableMapOf(2L to "Didi")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(), MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun outbox(enabled: Boolean) =
        DefaultFlowTaIncomeOutbox(FakePrefs(enabled), db.pendingFlowTaIncomeDao(), { names[it] }, scheduler, clock)

    private suspend fun FlowTaIncomeOutbox.enqueueTrip(tripId: Long = 5L, amount: Long = 5000L) =
        enqueue(tripId, platformId = 2L, amountMinor = amount, paymentMethod = PaymentMethod.TRANSFER, endedAt = 600L)

    @Test
    fun `enabled enqueues the trip and schedules a sync`() = runBlocking {
        outbox(true).enqueueTrip()

        val row = db.pendingFlowTaIncomeDao().oldest(10).single()
        assertEquals(5L, row.tripId)
        assertEquals(5000L, row.amountMinor)
        assertEquals("TRANSFER", row.paymentMethod)
        assertEquals(600L, row.endedAt)
        assertEquals("Didi", row.platformName)
        assertEquals(777L, row.createdAt)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `disabled queues nothing and schedules nothing`() = runBlocking {
        outbox(false).enqueueTrip()

        assertEquals(0, db.pendingFlowTaIncomeDao().count())
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun `enqueueing the same trip again does not reset the queued row`() = runBlocking {
        val outbox = outbox(true)
        outbox.enqueueTrip(amount = 5000L)
        db.pendingFlowTaIncomeDao().markFailed(5L, "timeout")

        outbox.enqueueTrip(amount = 9999L)

        val row = db.pendingFlowTaIncomeDao().oldest(10).single()
        assertEquals(5000L, row.amountMinor)
        assertEquals(1, row.attempts)
    }

    @Test
    fun `an unknown platform still queues the income with a blank name`() = runBlocking {
        outbox(true).enqueue(5L, platformId = 99L, amountMinor = 100L, paymentMethod = PaymentMethod.CASH, endedAt = 1L)

        assertEquals("", db.pendingFlowTaIncomeDao().oldest(10).single().platformName)
    }

    @Test
    fun `a scheduler failure never escapes and the row stays queued`() = runBlocking {
        scheduler.failure = IllegalStateException("WorkManager not initialized")

        outbox(true).enqueueTrip()

        assertEquals(1, db.pendingFlowTaIncomeDao().count())
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `a closed database never escapes`() = runBlocking {
        val outbox = outbox(true)
        db.close()

        outbox.enqueueTrip()
    }
}
