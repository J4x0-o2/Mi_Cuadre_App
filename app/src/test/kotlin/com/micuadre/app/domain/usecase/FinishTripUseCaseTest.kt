package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinishTripUseCaseTest {

    @Test
    fun `valid INDRIVER trip with positive amount is persisted`() = runTest {
        val repo = FakeShiftRepository(finishTripResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            sessionId = 1L,
            platform = Platform.INDRIVER,
            platformOtherLabel = null,
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 150_050L,
            paymentMethod = PaymentMethod.CASH
        )

        assertTrue(result is FinishTripUseCase.Result.Saved)
        assertEquals(1, repo.finishCalls.size)
        val call = repo.finishCalls.single()
        assertEquals(Platform.INDRIVER, call.platform)
        assertEquals(150_050L, call.amountMinor)
        assertEquals(PaymentMethod.CASH, call.method)
    }

    @Test
    fun `OTHER platform with blank label is rejected`() = runTest {
        val repo = FakeShiftRepository(finishTripResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            sessionId = 1L,
            platform = Platform.OTHER,
            platformOtherLabel = "   ",
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 100L,
            paymentMethod = PaymentMethod.CASH
        )

        assertEquals(FinishTripUseCase.Result.Invalid, result)
        assertEquals(0, repo.finishCalls.size)
    }

    @Test
    fun `zero amount is rejected`() = runTest {
        val repo = FakeShiftRepository(finishTripResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            sessionId = 1L,
            platform = Platform.DIDI,
            platformOtherLabel = null,
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
        val repo = FakeShiftRepository(finishTripResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            sessionId = 1L,
            platform = Platform.DIDI,
            platformOtherLabel = null,
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
        val repo = FakeShiftRepository(finishTripResult = false)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            sessionId = 1L,
            platform = Platform.INDRIVER,
            platformOtherLabel = null,
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 100L,
            paymentMethod = PaymentMethod.TRANSFER
        )

        assertEquals(FinishTripUseCase.Result.Invalid, result)
        assertEquals(1, repo.finishCalls.size)
    }

    @Test
    fun `OTHER platform with label is persisted`() = runTest {
        val repo = FakeShiftRepository(finishTripResult = true)
        val useCase = FinishTripUseCase(repo)

        val result = useCase(
            sessionId = 1L,
            platform = Platform.OTHER,
            platformOtherLabel = "Uber",
            startedAt = 1_000L,
            endedAt = 2_000L,
            amountMinor = 999L,
            paymentMethod = PaymentMethod.TRANSFER
        )

        assertTrue(result is FinishTripUseCase.Result.Saved)
        assertEquals("Uber", repo.finishCalls.single().platformOtherLabel)
    }
}

private data class FinishCall(
    val sessionId: Long,
    val platform: Platform,
    val platformOtherLabel: String?,
    val startedAt: Long,
    val endedAt: Long,
    val amountMinor: Long,
    val method: PaymentMethod
)

private class FakeShiftRepository(
    private val finishTripResult: Boolean
) : ShiftRepository {
    val finishCalls = mutableListOf<FinishCall>()

    override suspend fun createSession(startedAt: Long): Long = 1L

    override suspend fun endSession(sessionId: Long, endedAt: Long) {}

    override suspend fun activeSession() = null

    override fun observeTrips(sessionId: Long) =
        kotlinx.coroutines.flow.flowOf(emptyList<com.micuadre.app.domain.model.Trip>())

    override suspend fun insertTrip(trip: com.micuadre.app.domain.model.Trip) = 1L

    override suspend fun summary(sessionId: Long) =
        com.micuadre.app.domain.model.DailySummary(0L, 0, 0L, 0L)

    override suspend fun totalMinor(sessionId: Long) = 0L

    override suspend fun finishTrip(
        sessionId: Long,
        platform: Platform,
        platformOtherLabel: String?,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        finishCalls += FinishCall(sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod)
        return finishTripResult
    }
}
