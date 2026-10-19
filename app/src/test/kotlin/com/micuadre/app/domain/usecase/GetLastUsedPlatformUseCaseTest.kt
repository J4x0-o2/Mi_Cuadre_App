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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetLastUsedPlatformUseCaseTest {

    private val indriver = Platform(id = 1L, name = "InDriver")
    private val didi = Platform(id = 2L, name = "Didi")
    private val beat = Platform(id = 3L, name = "Beat")

    private fun trip(
        platform: Platform,
        payment: PaymentMethod? = PaymentMethod.CASH
    ) = Trip(
        id = 1L, sessionId = 1L, platformId = platform.id, platformName = platform.name,
        startedAt = 1L, endedAt = 2L, amountMinor = 100L, paymentMethod = payment
    )

    private fun useCase(last: Trip?, vararg visible: Platform) =
        GetLastUsedPlatformUseCase(LastTripRepository(last, visible.toList()))

    @Test
    fun `preselects the first visible platform when there are no finished trips`() = runTest {
        val result = useCase(null, indriver, didi)()

        assertEquals(PlatformSelection(indriver.id), result)
    }

    @Test
    fun `returns the platform of the most recent finished trip when it is still visible`() = runTest {
        val result = useCase(trip(didi), indriver, didi)()

        assertEquals(PlatformSelection(didi.id), result)
    }

    @Test
    fun `falls back to the first visible platform when the last one is hidden or deleted`() = runTest {
        val result = useCase(trip(beat), indriver, didi)()

        assertEquals(PlatformSelection(indriver.id), result)
    }

    @Test
    fun `selects no platform when none is visible`() = runTest {
        val result = useCase(trip(didi))()

        assertNull(result.platformId)
    }

    @Test
    fun `preselects the payment method of the last finished trip`() = runTest {
        val result = useCase(trip(didi, payment = PaymentMethod.TRANSFER), didi)()

        assertEquals(PlatformSelection(didi.id, PaymentMethod.TRANSFER), result)
    }

    @Test
    fun `keeps the last payment method even when the platform falls back`() = runTest {
        val result = useCase(trip(beat, payment = PaymentMethod.TRANSFER), indriver)()

        assertEquals(PlatformSelection(indriver.id, PaymentMethod.TRANSFER), result)
    }

    @Test
    fun `defaults to cash when there are no finished trips`() = runTest {
        assertEquals(PaymentMethod.CASH, useCase(null, indriver)().paymentMethod)
    }

    @Test
    fun `defaults to cash when the last trip has no payment method`() = runTest {
        assertEquals(PaymentMethod.CASH, useCase(trip(didi, payment = null), didi)().paymentMethod)
    }

    private class LastTripRepository(
        private val last: Trip?,
        private val visible: List<Platform>
    ) : ShiftRepository {
        override suspend fun lastFinishedTrip(): Trip? = last
        override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(visible)
        override suspend fun createSession(startedAt: Long): Long = error("unused")
        override suspend fun endSession(sessionId: Long, endedAt: Long) = error("unused")
        override suspend fun activeSession(): Session? = error("unused")
        override fun observeTrips(sessionId: Long): Flow<List<Trip>> = emptyFlow()
        override suspend fun insertTrip(trip: Trip): Long = error("unused")
        override suspend fun summary(sessionId: Long): DailySummary = error("unused")
        override suspend fun totalMinor(sessionId: Long): Long = error("unused")
        override suspend fun finishTrip(
            sessionId: Long, platformId: Long,
            startedAt: Long, endedAt: Long, amountMinor: Long, paymentMethod: PaymentMethod
        ): Boolean = error("unused")
    }
}
