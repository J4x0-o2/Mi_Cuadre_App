package com.micuadre.app.flowta

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
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
class FlowTaSyncRunnerTest {

    private class FakePrefs(initial: Boolean = true) : FlowTaSyncPreferenceRepository {
        val state = MutableStateFlow(initial)
        override fun observe(): Flow<Boolean> = state
        override suspend fun setEnabled(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class ScriptedClient(private val script: (PendingFlowTaIncomeEntity) -> FlowTaSendResult) : FlowTaClient {
        val sent = mutableListOf<Long>()
        override suspend fun linkStatus() = FlowTaLinkStatus.LINKED
        override suspend fun recordIncome(row: PendingFlowTaIncomeEntity): FlowTaSendResult {
            sent += row.tripId
            return script(row)
        }
    }

    private lateinit var db: MiCuadreDatabase
    private val prefs = FakePrefs()
    private val dao get() = db.pendingFlowTaIncomeDao()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(), MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun queue(vararg ids: Long) = ids.forEach {
        dao.enqueue(
            PendingFlowTaIncomeEntity(
                tripId = it, amountMinor = 1000L * it, paymentMethod = "CASH",
                endedAt = it, platformName = "Didi", createdAt = it
            )
        )
    }

    private fun runner(client: FlowTaClient, batchSize: Int = 20) =
        FlowTaSyncRunner(dao, client, prefs, batchSize)

    @Test
    fun `delivered rows are deleted in order`() = runBlocking {
        queue(1, 2, 3)
        val client = ScriptedClient { FlowTaSendResult.Delivered }

        assertEquals(FlowTaSyncRunner.Outcome.DONE, runner(client).drain())

        assertEquals(listOf(1L, 2L, 3L), client.sent)
        assertEquals(0, dao.count())
    }

    @Test
    fun `a rejected row is dropped and the drain continues`() = runBlocking {
        queue(1, 2)
        val client = ScriptedClient {
            if (it.tripId == 1L) FlowTaSendResult.Rejected("bad") else FlowTaSendResult.Delivered
        }

        assertEquals(FlowTaSyncRunner.Outcome.DONE, runner(client).drain())

        assertEquals(listOf(1L, 2L), client.sent)
        assertEquals(0, dao.count())
    }

    @Test
    fun `not linked stops the drain, keeps every undelivered row and succeeds`() = runBlocking {
        queue(1, 2, 3)
        val client = ScriptedClient {
            if (it.tripId == 2L) FlowTaSendResult.NotLinked else FlowTaSendResult.Delivered
        }

        assertEquals(FlowTaSyncRunner.Outcome.DONE, runner(client).drain())

        assertEquals(listOf(1L, 2L), client.sent)
        val left = dao.oldest(10)
        assertEquals(listOf(2L, 3L), left.map { it.tripId })
        assertEquals(1, left.first().attempts)
        assertEquals("NOT_LINKED", left.first().lastError)
        assertEquals(0, left.last().attempts)
    }

    @Test
    fun `an environment failure stops the drain, keeps rows, counts the attempt and asks for a retry`() = runBlocking {
        queue(1, 2)
        val client = ScriptedClient { FlowTaSendResult.EnvironmentFailure("SecurityException: key mismatch") }

        assertEquals(FlowTaSyncRunner.Outcome.RETRY, runner(client).drain())

        assertEquals(listOf(1L), client.sent)
        val left = dao.oldest(10)
        assertEquals(listOf(1L, 2L), left.map { it.tripId })
        assertEquals(1, left.first().attempts)
        assertEquals("SecurityException: key mismatch", left.first().lastError)
        assertEquals(0, left.last().attempts)
    }

    @Test
    fun `a poisoned head row does not block later rows`() = runBlocking {
        queue(1, 2, 3)
        val client = ScriptedClient {
            if (it.tripId == 1L) FlowTaSendResult.RowFailure("ERROR: bad") else FlowTaSendResult.Delivered
        }

        assertEquals(FlowTaSyncRunner.Outcome.RETRY, runner(client).drain())

        assertEquals(listOf(1L, 2L, 3L), client.sent)
        val left = dao.oldest(10).single()
        assertEquals(1L, left.tripId)
        assertEquals(1, left.attempts)
        assertEquals("ERROR: bad", left.lastError)
    }

    @Test
    fun `rows that keep failing are tried once per run, also across batches`() = runBlocking {
        queue(1, 2, 3, 4, 5)
        val client = ScriptedClient {
            if (it.tripId <= 2L) FlowTaSendResult.RowFailure("ERROR") else FlowTaSendResult.Delivered
        }

        assertEquals(FlowTaSyncRunner.Outcome.RETRY, runner(client, batchSize = 2).drain())

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), client.sent)
        assertEquals(listOf(1L, 2L), dao.oldest(10).map { it.tripId })

        client.sent.clear()
        assertEquals(FlowTaSyncRunner.Outcome.RETRY, runner(client, batchSize = 2).drain())
        assertEquals(listOf(1L, 2L), client.sent)
        assertEquals(listOf(2, 2), dao.oldest(10).map { it.attempts })
    }

    @Test
    fun `an environment failure after a row failure still stops and retries`() = runBlocking {
        queue(1, 2, 3)
        val client = ScriptedClient {
            when (it.tripId) {
                1L -> FlowTaSendResult.RowFailure("ERROR")
                2L -> FlowTaSendResult.EnvironmentFailure("SecurityException: x")
                else -> FlowTaSendResult.Delivered
            }
        }

        assertEquals(FlowTaSyncRunner.Outcome.RETRY, runner(client).drain())

        assertEquals(listOf(1L, 2L), client.sent)
        assertEquals(3, dao.count())
    }

    @Test
    fun `disabled does nothing and keeps rows`() = runBlocking {
        queue(1, 2)
        prefs.state.value = false
        val client = ScriptedClient { FlowTaSendResult.Delivered }

        assertEquals(FlowTaSyncRunner.Outcome.DONE, runner(client).drain())

        assertEquals(emptyList<Long>(), client.sent)
        assertEquals(2, dao.count())
    }

    @Test
    fun `an empty queue succeeds without calling FlowTa`() = runBlocking {
        val client = ScriptedClient { FlowTaSendResult.Delivered }

        assertEquals(FlowTaSyncRunner.Outcome.DONE, runner(client).drain())

        assertEquals(emptyList<Long>(), client.sent)
    }

    @Test
    fun `more rows than one batch are all drained`() = runBlocking {
        queue(1, 2, 3, 4, 5)
        val client = ScriptedClient { FlowTaSendResult.Delivered }

        assertEquals(FlowTaSyncRunner.Outcome.DONE, runner(client, batchSize = 2).drain())

        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), client.sent)
        assertEquals(0, dao.count())
    }
}
