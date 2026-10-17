package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceStartPolicyTest {

    private val connected = ShiftState.Connected(connectedSinceMs = 1L)

    @Test
    fun `any delivered intent must satisfy startForeground even without a known action`() {
        assertTrue(ServiceStartPolicy.requiresForeground(hasIntent = true))
    }

    @Test
    fun `a system restart without an intent has no foreground obligation`() {
        assertFalse(ServiceStartPolicy.requiresForeground(hasIntent = false))
    }

    @Test
    fun `idle service stops once rehydration finished and nothing is connecting`() {
        assertTrue(
            ServiceStartPolicy.shouldStopWhenIdle(
                state = ShiftState.Disconnected,
                rehydrated = true,
                connectPending = false,
                startCommandSeen = true
            )
        )
    }

    @Test
    fun `does not stop while rehydration is still running`() {
        assertFalse(
            ServiceStartPolicy.shouldStopWhenIdle(ShiftState.Disconnected, false, false, true)
        )
    }

    @Test
    fun `does not stop while a connect is in flight`() {
        assertFalse(
            ServiceStartPolicy.shouldStopWhenIdle(ShiftState.Disconnected, true, true, true)
        )
    }

    @Test
    fun `does not stop before the first start command arrived`() {
        assertFalse(
            ServiceStartPolicy.shouldStopWhenIdle(ShiftState.Disconnected, true, false, false)
        )
    }

    @Test
    fun `never stops an active shift`() {
        assertFalse(ServiceStartPolicy.shouldStopWhenIdle(connected, true, false, true))
        assertFalse(
            ServiceStartPolicy.shouldStopWhenIdle(ShiftState.OnTrip(1L, 2L), true, false, true)
        )
    }
}
