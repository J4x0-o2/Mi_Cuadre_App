package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishTripUseCaseTest {

    @Test
    fun `valid trip with positive amount is persisted by finishing the open trip`() = runTest {
        val repo = FakeShiftRepository(finishResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            tripId = 9L,
            platformId = 5L,
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 150_050L,
            paymentMethod = PaymentMethod.CASH
        )

        assertTrue(result is FinishTripUseCase.Result.Saved)
        assertEquals(1, repo.finishCalls.size)
        val call = repo.finishCalls.single()
        assertEquals(9L, call.tripId)
        assertEquals(2_000L, call.endedAt)
        assertEquals(5L, call.platformId)
        assertEquals(150_050L, call.amountMinor)
        assertEquals(PaymentMethod.CASH, call.method)
    }

    @Test
    fun `zero amount is rejected`() = runTest {
        val repo = FakeShiftRepository(finishResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            tripId = 9L,
            platformId = 2L,
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 0L,
            paymentMethod = PaymentMethod.CASH
        )

        assertEquals(FinishTripUseCase.Result.Invalid, result)
        assertEquals(0, repo.finishCalls.size)
    }

    @Test
    fun `endedAt before startedAt is rejected`() = runTest {
        val repo = FakeShiftRepository(finishResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            tripId = 9L,
            platformId = 2L,
            startedAt = 2_000L,
            endedAt = 1_000L,
            amountMinor = 100L,
            paymentMethod = PaymentMethod.CASH
        )

        assertEquals(FinishTripUseCase.Result.Invalid, result)
        assertEquals(0, repo.finishCalls.size)
    }

    @Test
    fun `repository failure surfaces as Invalid`() = runTest {
        val repo = FakeShiftRepository(finishResult = false)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            tripId = 9L,
            platformId = 2L,
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 100L,
            paymentMethod = PaymentMethod.TRANSFER
        )

        assertEquals(FinishTripUseCase.Result.Invalid, result)
        assertEquals(1, repo.finishCalls.size)
    }
}

private data class FinishCall(
    val tripId: Long,
    val platformId: Long,
    val endedAt: Long,
    val amountMinor: Long,
    val method: PaymentMethod
)

private class FakeShiftRepository(
    private val finishResult: Boolean
) : ShiftRepository {
    override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
    val finishCalls = mutableListOf<FinishCall>()

    override suspend fun createSession(startedAt: Long): Long = 1L

    override suspend fun endSession(sessionId: Long, endedAt: Long) {}

    override suspend fun activeSession() = null

    override fun observeTrips(sessionId: Long) =
        kotlinx.coroutines.flow.flowOf(emptyList<com.micuadre.app.domain.model.Trip>())

    override suspend fun insertTrip(trip: com.micuadre.app.domain.model.Trip) = 1L

    override suspend fun cancelInProgressTrip(sessionId: Long) {}

    override suspend fun summary(sessionId: Long) =
        com.micuadre.app.domain.model.DailySummary(0L, 0, 0L, 0L)

    override suspend fun totalMinor(sessionId: Long) = 0L

    override suspend fun openTrip(sessionId: Long, startedAt: Long): Long = error("unused")

    override suspend fun finishOpenTrip(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        finishCalls += FinishCall(tripId, platformId, endedAt, amountMinor, paymentMethod)
        return finishResult
    }
}
