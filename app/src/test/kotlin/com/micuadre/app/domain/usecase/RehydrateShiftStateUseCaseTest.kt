package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.util.FakeClock
import com.micuadre.app.util.TimeUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RehydrateShiftStateUseCaseTest {

    private companion object {
        // Realistic epoch-millis values (2026).
        const val SESSION_START = 1_790_000_000_000L
        const val TRIP_START = 1_790_000_120_000L
        // Boot-relative uptime written by the old build (~2.7 days of uptime).
        const val LEGACY_START = 235_000_000L
    }

    @Test
    fun `no active session returns Disconnected`() = runTest {
        val repo = FakeRehydrateRepository(activeSessionResult = null)
        val useCase = RehydrateShiftStateUseCase(repo)

        val result = useCase()

        assertSame(ShiftState.Disconnected, result.state)
        assertEquals(-1L, result.sessionId)
    }

    @Test
    fun `legacy boot-relative session is closed and not resumed`() = runTest {
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 7L, startedAt = LEGACY_START, endedAt = null)
        )

        val result = RehydrateShiftStateUseCase(repo)()

        assertSame(ShiftState.Disconnected, result.state)
        assertEquals(-1L, result.sessionId)
        assertEquals(listOf(7L to LEGACY_START), repo.endedSessions)
    }

    @Test
    fun `legacy session with an open trip is also closed and not resumed`() = runTest {
        val legacyTrip = Trip(
            id = 2L, sessionId = 7L, platformId = 2L, platformName = "Didi",
            startedAt = LEGACY_START + 1_000L, endedAt = null, amountMinor = null, paymentMethod = null
        )
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 7L, startedAt = LEGACY_START, endedAt = null),
            inProgressTripResult = legacyTrip
        )

        val result = RehydrateShiftStateUseCase(repo)()

        assertSame(ShiftState.Disconnected, result.state)
        assertEquals(1, repo.endedSessions.size)
    }

    @Test
    fun `a plausible session is not closed`() = runTest {
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 5L, startedAt = SESSION_START, endedAt = null)
        )

        RehydrateShiftStateUseCase(repo)()

        assertTrue(repo.endedSessions.isEmpty())
    }

    @Test
    fun `rehydrated shift keeps counting from the persisted start across a reboot`() = runTest {
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 5L, startedAt = SESSION_START, endedAt = null)
        )
        // Process died and the device rebooted; wall clock is three hours later.
        val clock = FakeClock(now = SESSION_START + 3L * 60 * 60 * 1000)

        val state = RehydrateShiftStateUseCase(repo)().state as ShiftState.Connected

        assertEquals(10_800L, TimeUtils.elapsedSeconds(state.connectedSinceMs, clock.nowMs()))
    }

    @Test
    fun `active session without in-progress trip returns Connected`() = runTest {
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 5L, startedAt = SESSION_START, endedAt = null),
            inProgressTripResult = null
        )
        val useCase = RehydrateShiftStateUseCase(repo)

        val result = useCase()

        assertTrue(result.state is ShiftState.Connected)
        assertEquals(SESSION_START, (result.state as ShiftState.Connected).connectedSinceMs)
        assertEquals(5L, result.sessionId)
        assertNull(result.tripId)
    }

    @Test
    fun `active session with in-progress trip returns OnTrip with trip start`() = runTest {
        val inProgress = Trip(
            id = 41L,
            sessionId = 5L,
            platformId = 0L, platformName = "",
            startedAt = TRIP_START,
            endedAt = null,
            amountMinor = null,
            paymentMethod = null
        )
        val repo = FakeRehydrateRepository(
            activeSessionResult = Session(id = 5L, startedAt = SESSION_START, endedAt = null),
            inProgressTripResult = inProgress
        )
        val useCase = RehydrateShiftStateUseCase(repo)

        val result = useCase()

        assertTrue(result.state is ShiftState.OnTrip)
        val state = result.state as ShiftState.OnTrip
        assertEquals(SESSION_START, state.connectedSinceMs)
        assertEquals(TRIP_START, state.tripStartedAtMs)
        assertEquals(5L, result.sessionId)
        assertEquals(41L, result.tripId)
    }
}

private class FakeRehydrateRepository(
    private val activeSessionResult: Session?,
    private val inProgressTripResult: Trip? = null
) : ShiftRepository {
    override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())

    override suspend fun createSession(startedAt: Long): Long = 1L

    val endedSessions = mutableListOf<Pair<Long, Long>>()

    override suspend fun endSession(sessionId: Long, endedAt: Long) {
        endedSessions += sessionId to endedAt
    }

    override suspend fun activeSession(): Session? = activeSessionResult

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())

    override suspend fun insertTrip(trip: Trip): Long = 1L

    override suspend fun cancelInProgressTrip(sessionId: Long) {}

    override suspend fun summary(sessionId: Long) =
        com.micuadre.app.domain.model.DailySummary(0L, 0, 0L, 0L)

    override suspend fun totalMinor(sessionId: Long): Long = 0L

    override suspend fun inProgressTrip(sessionId: Long): Trip? = inProgressTripResult

    override suspend fun openTrip(sessionId: Long, startedAt: Long): Long = error("unused")
    override suspend fun finishOpenTrip(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean = error("unused")
}
