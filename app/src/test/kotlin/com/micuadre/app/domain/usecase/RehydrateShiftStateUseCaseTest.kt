package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RehydrateShiftStateUseCaseTest {

    @Test
    fun `no active session returns Disconnected`() = runTest {
        val repo = FakeRehydrateRepository(activeSessionResult = null)
        val useCase = RehydrateShiftStateUseCase(repo)

        val result = useCase()

        assertSame(ShiftState.Disconnected, result.state)
        assertEquals(-1L, result.sessionId)
    }

    @Test
    fun `active session without in-progress trip returns Connected`() = runTest {
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 5L, startedAt = 10_000L, endedAt = null),
            inProgressTripResult = null
        )
        val useCase = RehydrateShiftStateUseCase(repo)

        val result = useCase()

        assertTrue(result.state is ShiftState.Connected)
        assertEquals(10_000L, (result.state as ShiftState.Connected).connectedSinceMs)
        assertEquals(5L, result.sessionId)
    }

    @Test
    fun `active session with in-progress trip returns OnTrip with trip start`() = runTest {
        val inProgress = Trip(
            id = 1L,
            sessionId = 5L,
            platform = Platform.INDRIVER,
            platformOtherLabel = null,
            startedAt = 12_000L,
            endedAt = null,
            amountMinor = null,
            paymentMethod = null
        )
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 5L, startedAt = 10_000L, endedAt = null),
            inProgressTripResult = inProgress
        )
        val useCase = RehydrateShiftStateUseCase(repo)

        val result = useCase()

        assertTrue(result.state is ShiftState.OnTrip)
        val state = result.state as ShiftState.OnTrip
        assertEquals(10_000L, state.connectedSinceMs)
        assertEquals(12_000L, state.tripStartedAtMs)
        assertEquals(5L, result.sessionId)
    }
}

private class FakeRehydrateRepository(
    private val activeSessionResult: Session?,
    private val inProgressTripResult: Trip? = null
) : ShiftRepository {

    override suspend fun createSession(startedAt: Long): Long = 1L

    override suspend fun endSession(sessionId: Long, endedAt: Long) {}

    override suspend fun activeSession(): Session? = activeSessionResult

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())

    override suspend fun insertTrip(trip: Trip): Long = 1L

    override suspend fun summary(sessionId: Long) =
        com.micuadre.app.domain.model.DailySummary(0L, 0, 0L, 0L)

    override suspend fun totalMinor(sessionId: Long): Long = 0L

    override suspend fun inProgressTrip(sessionId: Long): Trip? = inProgressTripResult

    override suspend fun finishTrip(
        sessionId: Long,
        platform: Platform,
        platformOtherLabel: String?,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean = true
}
