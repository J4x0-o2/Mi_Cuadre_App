package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinishTripReducerTest {

    private val reducer = FinishTripReducer(ShiftStateMachine(nowMs = { 0L }))
    private val onTrip = ShiftState.OnTrip(connectedSinceMs = 1_790_000_000_000L, tripStartedAtMs = 1_790_000_100_000L)

    @Test
    fun `rejected finish keeps the trip open and publishes the error`() {
        val result = reducer.reduce(onTrip, FinishTripHandler.Outcome.Rejected)

        assertEquals(onTrip, result.state)
        assertEquals(ShiftError.InvalidFinishTrip, result.error)
    }

    @Test
    fun `finished trip returns to Connected without an error`() {
        val result = reducer.reduce(onTrip, FinishTripHandler.Outcome.Finished)

        assertEquals(ShiftState.Connected(connectedSinceMs = onTrip.connectedSinceMs), result.state)
        assertNull(result.error)
    }
}
