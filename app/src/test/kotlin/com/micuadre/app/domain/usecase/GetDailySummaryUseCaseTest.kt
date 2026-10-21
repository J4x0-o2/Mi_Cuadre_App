package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetDailySummaryUseCaseTest {

    private val base = DailySummary(totalAmountMinor = 50_000L, tripCount = 3, totalDurationMs = 1L, avgDurationMs = 1L)
    private val fuel = SessionDeductionAmount(sessionId = 7L, deductionId = 1L, name = "Gasolina", amountMinor = 15_000L)
    private val food = SessionDeductionAmount(sessionId = 7L, deductionId = 2L, name = "Comida", amountMinor = 3_000L)

    @Test
    fun `adds the session deductions to the shift summary`() = runTest {
        val deductions = FakeDeductions(listOf(fuel, food))

        val summary = GetDailySummaryUseCase(FakeShifts(base), deductions)(sessionId = 7L)

        assertEquals(listOf(fuel, food), summary.deductions)
        assertEquals(base, summary.copy(deductions = emptyList()))
        assertEquals(listOf(7L), deductions.asked)
    }

    @Test
    fun `a shift without deductions keeps an empty list`() = runTest {
        val summary = GetDailySummaryUseCase(FakeShifts(base), FakeDeductions(emptyList()))(sessionId = 7L)

        assertTrue(summary.deductions.isEmpty())
    }

    @Test
    fun `a failing deductions read still returns the gross summary`() = runTest {
        val deductions = FakeDeductions(emptyList(), failure = IllegalStateException("db closed"))

        val summary = GetDailySummaryUseCase(FakeShifts(base), deductions)(sessionId = 7L)

        assertEquals(base, summary)
    }

    private class FakeDeductions(
        private val amounts: List<SessionDeductionAmount>,
        private val failure: Throwable? = null
    ) : DeductionRepository {
        val asked = mutableListOf<Long>()

        override suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount> {
            asked += sessionId
            failure?.let { throw it }
            return amounts
        }

        override fun observeActive(): Flow<List<Deduction>> = flowOf(emptyList())
        override fun observeVisible(): Flow<List<Deduction>> = flowOf(emptyList())
        override suspend fun create(name: String, usualAmountMinor: Long?): DeductionResult = error("unused")
        override suspend fun rename(id: Long, name: String): DeductionResult = error("unused")
        override suspend fun update(id: Long, name: String, usualAmountMinor: Long?): DeductionResult = error("unused")
        override suspend fun setVisible(id: Long, visible: Boolean): DeductionResult = error("unused")
        override suspend fun setUsualAmount(id: Long, amountMinor: Long?): DeductionResult = error("unused")
        override suspend fun delete(id: Long): DeductionResult = error("unused")
        override suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>): DeductionResult =
            error("unused")
        override fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>> =
            flowOf(emptyList())
    }

    private class FakeShifts(private val summary: DailySummary) : ShiftRepository {
        override suspend fun summary(sessionId: Long): DailySummary = summary

        override suspend fun createSession(startedAt: Long): Long = error("unused")
        override suspend fun endSession(sessionId: Long, endedAt: Long) = error("unused")
        override suspend fun activeSession(): Session? = error("unused")
        override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
        override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
        override suspend fun insertTrip(trip: Trip): Long = error("unused")
        override suspend fun cancelInProgressTrip(sessionId: Long) = error("unused")
        override suspend fun totalMinor(sessionId: Long): Long = error("unused")
        override suspend fun finishTrip(
            sessionId: Long,
            platformId: Long,
            startedAt: Long,
            endedAt: Long,
            amountMinor: Long,
            paymentMethod: PaymentMethod
        ): Boolean = error("unused")
    }
}
