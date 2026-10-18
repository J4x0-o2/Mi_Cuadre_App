package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.PendingFinishRequest.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LaunchRequestsTest {

    private val onTrip = ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = 2L)
    private val connected = ShiftState.Connected(connectedSinceMs = 1L)

    @Test
    fun `finish action routes to the finish sheet`() {
        assertEquals(
            LaunchRequest.OPEN_FINISH_SHEET,
            LaunchRouting.requestFor(LaunchRouting.ACTION_OPEN_FINISH_TRIP)
        )
    }

    @Test
    fun `launcher and unknown actions route to nothing`() {
        assertNull(LaunchRouting.requestFor(null))
        assertNull(LaunchRouting.requestFor("android.intent.action.MAIN"))
    }

    @Test
    fun `without a request nothing opens`() {
        assertEquals(Decision.None, PendingFinishRequest().onState(onTrip))
    }

    @Test
    fun `request opens the sheet as soon as the shift is on a trip`() {
        val pending = PendingFinishRequest().apply { request() }

        assertEquals(Decision.OpenSheet, pending.onState(onTrip))
    }

    @Test
    fun `request is consumed once so rotation does not reopen the sheet`() {
        val pending = PendingFinishRequest().apply { request() }

        pending.onState(onTrip)

        assertEquals(Decision.None, pending.onState(onTrip))
    }

    @Test
    fun `request waits while the state is still disconnected on cold start`() {
        val pending = PendingFinishRequest().apply { request() }

        assertEquals(Decision.None, pending.onState(ShiftState.Disconnected))
        assertEquals(Decision.OpenSheet, pending.onState(onTrip))
    }

    @Test
    fun `stale request is dropped when the trip already ended`() {
        val pending = PendingFinishRequest().apply { request() }

        assertEquals(Decision.None, pending.onState(connected))
        assertEquals(Decision.None, pending.onState(onTrip))
    }

    @Test
    fun `expired request never opens the sheet`() {
        val pending = PendingFinishRequest().apply { request() }

        pending.expire()

        assertEquals(Decision.None, pending.onState(onTrip))
    }
}
