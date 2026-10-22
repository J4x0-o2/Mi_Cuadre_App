package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StartTripUseCaseTest {

    @Test
    fun `persists an open trip and returns its id and persisted start`() = runTest {
        val repo = RecordingRepository()

        val started = StartTripUseCase(repo)(sessionId = 7L, startedAt = 1_000L)

        assertEquals(listOf(7L to 1_000L), repo.opened)
        assertEquals(StartTripUseCase.Started(tripId = 33L, startedAt = 1_000L), started)
    }

    @Test
    fun `an already open trip wins and its persisted start is reported`() = runTest {
        val repo = RecordingRepository(existingStart = 500L)

        val started = StartTripUseCase(repo)(sessionId = 7L, startedAt = 1_000L)

        assertEquals(StartTripUseCase.Started(tripId = 33L, startedAt = 500L), started)
    }

    @Test
    fun `a failing insert is propagated so no ride starts in memory only`() = runTest {
        val repo = RecordingRepository(openFailure = IllegalStateException("disk full"))

        try {
            StartTripUseCase(repo)(sessionId = 7L, startedAt = 1_000L)
            fail("expected the failure to propagate")
        } catch (expected: IllegalStateException) {
            assertTrue(repo.opened.isEmpty())
        }
    }

    private class RecordingRepository(
        private val existingStart: Long? = null,
        private val openFailure: Throwable? = null
    ) : ShiftRepository {
        val opened = mutableListOf<Pair<Long, Long>>()
        private var persistedStart = 0L

        override suspend fun openTrip(sessionId: Long, startedAt: Long): Long {
            openFailure?.let { throw it }
            opened += sessionId to startedAt
            persistedStart = existingStart ?: startedAt
            return 33L
        }

        override suspend fun inProgressTrip(sessionId: Long): Trip = Trip(
            id = 33L, sessionId = sessionId, platformId = 0L, platformName = "",
            startedAt = persistedStart, endedAt = null, amountMinor = null, paymentMethod = null
        )

        override suspend fun finishOpenTrip(
            tripId: Long,
            platformId: Long,
            endedAt: Long,
            amountMinor: Long,
            paymentMethod: PaymentMethod
        ): Boolean = error("unused")

        override suspend fun createSession(startedAt: Long): Long = error("unused")
        override suspend fun endSession(sessionId: Long, endedAt: Long) = error("unused")
        override suspend fun activeSession(): Session? = error("unused")
        override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
        override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
        override suspend fun cancelInProgressTrip(sessionId: Long) = error("unused")
        override suspend fun insertTrip(trip: Trip): Long = error("unused")
        override suspend fun summary(sessionId: Long): DailySummary = error("unused")
        override suspend fun totalMinor(sessionId: Long): Long = error("unused")
    }
}
