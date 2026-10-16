package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftServiceStateMachineTest {

    private var now = 10_000L
    private val machine = ShiftStateMachine(nowMs = { now })

    @Test
    fun `initial state is Disconnected`() {
        // Sanity check: the machine has no implicit "initial state" — the
        // caller supplies the starting state explicitly. A fresh caller
        // starts from Disconnected, and Connect moves us to Connected.
        val s = machine.transition(ShiftState.Disconnected, ShiftStateMachine.Action.Connect)
        assertTrue(s is ShiftState.Connected)
        assertEquals(10_000L, (s as ShiftState.Connected).connectedSinceMs)
    }

    @Test
    fun `Disconnected connect yields Connected at now`() {
        now = 5_000L
        val s = machine.transition(ShiftState.Disconnected, ShiftStateMachine.Action.Connect)
        assertTrue(s is ShiftState.Connected)
        assertEquals(5_000L, (s as ShiftState.Connected).connectedSinceMs)
    }

    @Test
    fun `Connected startTrip yields OnTrip preserving connectedSince`() {
        now = 1_000L
        val connected = ShiftState.Connected(connectedSinceMs = 1_000L)
        now = 2_500L
        val s = machine.transition(connected, ShiftStateMachine.Action.StartTrip(tripStartedAtMs = 2_500L))
        assertTrue(s is ShiftState.OnTrip)
        s as ShiftState.OnTrip
        assertEquals(1_000L, s.connectedSinceMs)
        assertEquals(2_500L, s.tripStartedAtMs)
    }

    @Test
    fun `OnTrip finishTrip yields Connected preserving connectedSince`() {
        val onTrip = ShiftState.OnTrip(connectedSinceMs = 1_000L, tripStartedAtMs = 2_000L)
        val s = machine.transition(onTrip, ShiftStateMachine.Action.FinishTrip)
        assertTrue(s is ShiftState.Connected)
        assertEquals(1_000L, (s as ShiftState.Connected).connectedSinceMs)
    }

    @Test
    fun `Connected disconnect yields Disconnected`() {
        val connected = ShiftState.Connected(connectedSinceMs = 1_000L)
        val s = machine.transition(connected, ShiftStateMachine.Action.Disconnect)
        assertSame(ShiftState.Disconnected, s)
    }

    @Test
    fun `EndShift from Connected yields Disconnected`() {
        val connected = ShiftState.Connected(connectedSinceMs = 1_000L)
        val s = machine.transition(connected, ShiftStateMachine.Action.EndShift)
        assertSame(ShiftState.Disconnected, s)
    }

    @Test
    fun `EndShift from OnTrip yields Disconnected`() {
        val onTrip = ShiftState.OnTrip(connectedSinceMs = 1_000L, tripStartedAtMs = 2_000L)
        val s = machine.transition(onTrip, ShiftStateMachine.Action.EndShift)
        assertSame(ShiftState.Disconnected, s)
    }

    @Test
    fun `StartTrip from Disconnected is a no-op`() {
        val s = machine.transition(ShiftState.Disconnected, ShiftStateMachine.Action.StartTrip(tripStartedAtMs = 1_000L))
        assertSame(ShiftState.Disconnected, s)
    }

    @Test
    fun `StartTrip from OnTrip is a no-op`() {
        val onTrip = ShiftState.OnTrip(connectedSinceMs = 1_000L, tripStartedAtMs = 2_000L)
        val s = machine.transition(onTrip, ShiftStateMachine.Action.StartTrip(tripStartedAtMs = 3_000L))
        assertSame(onTrip, s)
    }

    @Test
    fun `FinishTrip from Connected is a no-op`() {
        val connected = ShiftState.Connected(connectedSinceMs = 1_000L)
        val s = machine.transition(connected, ShiftStateMachine.Action.FinishTrip)
        assertSame(connected, s)
    }

    @Test
    fun `FinishTrip from Disconnected is a no-op`() {
        val s = machine.transition(ShiftState.Disconnected, ShiftStateMachine.Action.FinishTrip)
        assertSame(ShiftState.Disconnected, s)
    }

    @Test
    fun `Disconnect from Disconnected is a no-op`() {
        val s = machine.transition(ShiftState.Disconnected, ShiftStateMachine.Action.Disconnect)
        assertSame(ShiftState.Disconnected, s)
    }

    @Test
    fun `Disconnect from OnTrip is a no-op`() {
        val onTrip = ShiftState.OnTrip(connectedSinceMs = 1_000L, tripStartedAtMs = 2_000L)
        val s = machine.transition(onTrip, ShiftStateMachine.Action.Disconnect)
        assertSame(onTrip, s)
    }

    @Test
    fun `Connect from Connected is a no-op`() {
        val connected = ShiftState.Connected(connectedSinceMs = 1_000L)
        val s = machine.transition(connected, ShiftStateMachine.Action.Connect)
        assertSame(connected, s)
    }

    @Test
    fun `full happy path Disconnected Connected OnTrip Connected Disconnected`() {
        now = 100L
        var s: ShiftState = ShiftState.Disconnected
        s = machine.transition(s, ShiftStateMachine.Action.Connect)
        assertTrue(s is ShiftState.Connected)

        now = 500L
        s = machine.transition(s, ShiftStateMachine.Action.StartTrip(tripStartedAtMs = 500L))
        assertTrue(s is ShiftState.OnTrip)

        s = machine.transition(s, ShiftStateMachine.Action.FinishTrip)
        assertTrue(s is ShiftState.Connected)

        s = machine.transition(s, ShiftStateMachine.Action.Disconnect)
        assertSame(ShiftState.Disconnected, s)
    }
}
