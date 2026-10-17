package com.micuadre.app.presentation

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSnapshotFlowTest {

    private fun trip(id: Long, amount: Long) = Trip(
        id = id,
        sessionId = 7L,
        platform = Platform.DIDI,
        platformOtherLabel = null,
        startedAt = 1_000L,
        endedAt = 2_000L,
        amountMinor = amount,
        paymentMethod = PaymentMethod.CASH
    )

    @Test
    fun `later shift-state emissions are applied even though trips never complete`() = runTest {
        val states = MutableSharedFlow<ShiftState>()
        // Never-completing, like a Room Flow.
        val trips = MutableStateFlow(listOf(trip(1, 100L)))
        val seen = mutableListOf<HomeSnapshot>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            homeSnapshots(states, activeSessionId = { 7L }, observeTrips = { trips }).toList(seen)
        }

        states.emit(ShiftState.Connected(connectedSinceMs = 10L))
        states.emit(ShiftState.OnTrip(connectedSinceMs = 10L, tripStartedAtMs = 20L))
        states.emit(ShiftState.Disconnected)

        assertEquals(ShiftState.Disconnected, seen.last().shiftState)
        assertTrue(seen.any { it.shiftState is ShiftState.OnTrip })
        job.cancel()
    }

    @Test
    fun `trips and total follow the live trips flow while connected`() = runTest {
        val states = MutableStateFlow<ShiftState>(ShiftState.Connected(connectedSinceMs = 10L))
        val trips = MutableStateFlow(listOf(trip(1, 100L)))
        val seen = mutableListOf<HomeSnapshot>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            homeSnapshots(states, activeSessionId = { 7L }, observeTrips = { trips }).toList(seen)
        }
        assertEquals(100L, seen.last().totalMinor)

        trips.value = listOf(trip(1, 100L), trip(2, 250L))

        assertEquals(2, seen.last().trips.size)
        assertEquals(350L, seen.last().totalMinor)
        job.cancel()
    }

    @Test
    fun `connected with no active session yields empty trips`() = runTest {
        val states = MutableStateFlow<ShiftState>(ShiftState.Connected(connectedSinceMs = 10L))
        val result = homeSnapshots(
            states,
            activeSessionId = { null },
            observeTrips = { error("must not be called") }
        ).firstSnapshot()

        assertTrue(result.trips.isEmpty())
        assertEquals(0L, result.totalMinor)
    }

    @Test
    fun `paused shift keeps the open session trips and total`() = runTest {
        val states = MutableStateFlow<ShiftState>(ShiftState.Disconnected)
        val result = homeSnapshots(
            states,
            activeSessionId = { 7L },
            observeTrips = { MutableStateFlow(listOf(trip(1, 100L), trip(2, 250L))) }
        ).firstSnapshot()

        assertEquals(2, result.trips.size)
        assertEquals(350L, result.totalMinor)
        assertTrue(result.hasOpenShift)
    }

    @Test
    fun `disconnected without an open session is empty`() = runTest {
        val states = MutableStateFlow<ShiftState>(ShiftState.Disconnected)
        val result = homeSnapshots(
            states,
            activeSessionId = { null },
            observeTrips = { error("must not be called") }
        ).firstSnapshot()

        assertTrue(result.trips.isEmpty())
        assertEquals(0L, result.totalMinor)
        assertFalse(result.hasOpenShift)
    }

    private suspend fun Flow<HomeSnapshot>.firstSnapshot(): HomeSnapshot =
        first()
}
