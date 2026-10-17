package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformSelection
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetLastUsedPlatformUseCaseTest {

    private fun trip(platform: Platform, label: String? = null) = Trip(
        id = 1L, sessionId = 1L, platform = platform, platformOtherLabel = label,
        startedAt = 1L, endedAt = 2L, amountMinor = 100L, paymentMethod = PaymentMethod.CASH
    )

    @Test
    fun `defaults to InDriver when there are no finished trips`() = runTest {
        val result = GetLastUsedPlatformUseCase(LastTripRepository(null))()

        assertEquals(PlatformSelection(Platform.INDRIVER), result)
    }

    @Test
    fun `returns the platform of the most recent finished trip`() = runTest {
        val result = GetLastUsedPlatformUseCase(LastTripRepository(trip(Platform.DIDI)))()

        assertEquals(PlatformSelection(Platform.DIDI), result)
    }

    @Test
    fun `keeps the free text label when the last platform was Otra`() = runTest {
        val result = GetLastUsedPlatformUseCase(LastTripRepository(trip(Platform.OTHER, "Uber")))()

        assertEquals(PlatformSelection(Platform.OTHER, "Uber"), result)
    }

    @Test
    fun `falls back to InDriver when the last Otra trip lost its label`() = runTest {
        val result = GetLastUsedPlatformUseCase(LastTripRepository(trip(Platform.OTHER, "  ")))()

        assertEquals(PlatformSelection(Platform.INDRIVER), result)
    }

    private class LastTripRepository(private val last: Trip?) : ShiftRepository {
        override suspend fun lastFinishedTrip(): Trip? = last
        override suspend fun createSession(startedAt: Long): Long = error("unused")
        override suspend fun endSession(sessionId: Long, endedAt: Long) = error("unused")
        override suspend fun activeSession(): Session? = error("unused")
        override fun observeTrips(sessionId: Long): Flow<List<Trip>> = emptyFlow()
        override suspend fun insertTrip(trip: Trip): Long = error("unused")
        override suspend fun summary(sessionId: Long): DailySummary = error("unused")
        override suspend fun totalMinor(sessionId: Long): Long = error("unused")
        override suspend fun finishTrip(
            sessionId: Long, platform: Platform, platformOtherLabel: String?,
            startedAt: Long, endedAt: Long, amountMinor: Long, paymentMethod: PaymentMethod
        ): Boolean = error("unused")
    }
}
