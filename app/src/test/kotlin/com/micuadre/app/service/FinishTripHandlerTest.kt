package com.micuadre.app.service

import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.domain.usecase.FinishTripUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FinishTripHandlerTest {

    private fun handler(repo: HandlerFakeRepository) = FinishTripHandler(FinishTripUseCase(repo))

    private suspend fun FinishTripHandler.run(
        platformId: Long? = 2L,
        amount: Long = 5_000L,
        payment: String? = "CASH"
    ) = handle(
        sessionId = 1L,
        tripStartedAt = 1_000L,
        endedAt = 2_000L,
        platformId = platformId,
        amountMinor = amount,
        paymentName = payment
    )

    @Test
    fun `valid payload is saved and reported as Finished`() = runTest {
        val repo = HandlerFakeRepository(saves = true)
        assertEquals(FinishTripHandler.Outcome.Finished, handler(repo).run())
        assertEquals(1, repo.finishCalls)
    }

    @Test
    fun `unknown or deleted platform is rejected by the repository so the trip stays open`() = runTest {
        val repo = HandlerFakeRepository(saves = false)
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run(platformId = 99L))
        assertEquals(1, repo.finishCalls)
    }

    @Test
    fun `non-positive platform id is rejected without touching storage`() = runTest {
        val repo = HandlerFakeRepository(saves = true)
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run(platformId = 0L))
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run(platformId = -3L))
        assertEquals(0, repo.finishCalls)
    }

    @Test
    fun `missing platform or payment is rejected`() = runTest {
        val repo = HandlerFakeRepository(saves = true)
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run(platformId = null))
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run(payment = null))
        assertEquals(0, repo.finishCalls)
    }

    @Test
    fun `non-positive amount is rejected`() = runTest {
        val repo = HandlerFakeRepository(saves = true)
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run(amount = 0L))
        assertEquals(0, repo.finishCalls)
    }

    @Test
    fun `persistence exception is reported as Rejected instead of crashing`() = runTest {
        val repo = HandlerFakeRepository(saves = true, failure = IllegalStateException("disk full"))
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run())
        assertEquals(1, repo.finishCalls)
    }

    @Test
    fun `repository failure is reported as Rejected so the trip stays open`() = runTest {
        val repo = HandlerFakeRepository(saves = false)
        assertEquals(FinishTripHandler.Outcome.Rejected, handler(repo).run())
        assertEquals(1, repo.finishCalls)
    }
}

private class HandlerFakeRepository(
    private val saves: Boolean,
    private val failure: Throwable? = null
) : ShiftRepository {
    override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
    var finishCalls = 0

    override suspend fun createSession(startedAt: Long): Long = 1L
    override suspend fun endSession(sessionId: Long, endedAt: Long) {}
    override suspend fun activeSession() = null
    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
    override suspend fun insertTrip(trip: Trip) = 1L
    override suspend fun summary(sessionId: Long) = DailySummary(0L, 0, 0L, 0L)
    override suspend fun totalMinor(sessionId: Long) = 0L
    override suspend fun finishTrip(
        sessionId: Long,
        platformId: Long,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        finishCalls++
        failure?.let { throw it }
        return saves
    }
}
