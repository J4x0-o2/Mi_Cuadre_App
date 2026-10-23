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
import org.junit.Test

class CancelTripUseCaseTest {

    @Test
    fun `discards the in-progress trip of the session`() = runTest {
        val repo = RecordingRepository()

        CancelTripUseCase(repo)(sessionId = 7L)

        assertEquals(listOf(7L), repo.cancelled)
    }

    private class RecordingRepository : ShiftRepository {
        val cancelled = mutableListOf<Long>()

        override suspend fun cancelInProgressTrip(sessionId: Long) {
            cancelled += sessionId
        }

        override suspend fun createSession(startedAt: Long): Long = error("unused")
        override suspend fun endSession(sessionId: Long, endedAt: Long) = error("unused")
        override suspend fun activeSession(): Session? = error("unused")
        override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
        override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
        override suspend fun insertTrip(trip: Trip): Long = error("unused")
        override suspend fun summary(sessionId: Long): DailySummary = error("unused")
        override suspend fun totalMinor(sessionId: Long): Long = error("unused")
        override suspend fun openTrip(sessionId: Long, startedAt: Long): Trip = error("unused")
        override suspend fun finishOpenTrip(
            tripId: Long,
            platformId: Long,
            endedAt: Long,
            amountMinor: Long,
            paymentMethod: PaymentMethod
        ): Boolean = error("unused")
    }
}
