package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubblePanelModelTest {

    private val connected = ShiftState.Connected(connectedSinceMs = 1L)
    private val onTrip = ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = 2L)

    @Test
    fun `connected offers starting a ride and opening the app`() {
        val model = BubblePanelModel.of(connected, confirmingCancel = false)!!

        assertEquals(PanelStatus.CONNECTED, model.status)
        assertEquals(listOf(PanelAction.START_TRIP, PanelAction.OPEN_APP), model.actions)
    }

    @Test
    fun `on a trip offers finish, cancel and opening the app`() {
        val model = BubblePanelModel.of(onTrip, confirmingCancel = false)!!

        assertEquals(PanelStatus.ON_TRIP, model.status)
        assertEquals(
            listOf(PanelAction.FINISH_TRIP, PanelAction.ASK_CANCEL_TRIP, PanelAction.OPEN_APP),
            model.actions
        )
    }

    @Test
    fun `confirming a cancel swaps the actions for confirm and back`() {
        val model = BubblePanelModel.of(onTrip, confirmingCancel = true)!!

        assertEquals(true, model.confirmingCancel)
        assertEquals(listOf(PanelAction.CONFIRM_CANCEL_TRIP, PanelAction.KEEP_TRIP), model.actions)
    }

    @Test
    fun `a confirmation that no longer applies is ignored while connected`() {
        val model = BubblePanelModel.of(connected, confirmingCancel = true)!!

        assertEquals(false, model.confirmingCancel)
        assertEquals(listOf(PanelAction.START_TRIP, PanelAction.OPEN_APP), model.actions)
    }

    @Test
    fun `there is no panel while disconnected`() {
        assertNull(BubblePanelModel.of(ShiftState.Disconnected, confirmingCancel = false))
    }

    @Test
    fun `the ride timer is shown on a trip and the connected timer otherwise`() {
        assertEquals(false, BubblePanelModel.of(connected, false)!!.showsRideTime)
        assertEquals(true, BubblePanelModel.of(onTrip, false)!!.showsRideTime)
    }
}
