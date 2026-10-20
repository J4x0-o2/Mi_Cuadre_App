package com.micuadre.app.overlay

import com.micuadre.app.domain.model.ShiftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BubblePanelCoordinatorTest {

    private class RecordingCommands : BubbleCommands {
        val calls = mutableListOf<String>()
        override fun startTrip() { calls += "start" }
        override fun cancelTrip(tripStartedAtMs: Long) { calls += "cancel:$tripStartedAtMs" }
        override fun openFinishTrip() { calls += "finish" }
        override fun openApp() { calls += "open" }
    }

    private val commands = RecordingCommands()
    private val coordinator = BubblePanelCoordinator(commands)
    private val connected = ShiftState.Connected(connectedSinceMs = 1L)
    private val tripA = ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = 10L)
    private val tripB = ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = 20L)

    private val ui get() = coordinator.ui.value

    @Test
    fun `starts collapsed`() {
        assertEquals(false, ui.expanded)
    }

    @Test
    fun `tapping the bubble expands the panel`() {
        coordinator.onShiftState(connected)

        coordinator.onBubbleTapped()

        assertEquals(true, ui.expanded)
    }

    @Test
    fun `collapsing closes the panel`() {
        coordinator.onShiftState(connected)
        coordinator.onBubbleTapped()

        coordinator.onCollapse()

        assertEquals(false, ui.expanded)
    }

    @Test
    fun `start ride runs once and collapses`() {
        coordinator.onShiftState(connected)
        coordinator.onBubbleTapped()

        coordinator.onAction(PanelAction.START_TRIP)

        assertEquals(listOf("start"), commands.calls)
        assertEquals(false, ui.expanded)
    }

    @Test
    fun `start ride is ignored when the shift is not connected`() {
        coordinator.onShiftState(tripA)

        coordinator.onAction(PanelAction.START_TRIP)

        assertEquals(emptyList<String>(), commands.calls)
    }

    @Test
    fun `finish ride opens the finish sheet through the app and collapses`() {
        coordinator.onShiftState(tripA)
        coordinator.onBubbleTapped()

        coordinator.onAction(PanelAction.FINISH_TRIP)

        assertEquals(listOf("finish"), commands.calls)
        assertEquals(false, ui.expanded)
    }

    @Test
    fun `finish ride is ignored when there is no ride`() {
        coordinator.onShiftState(connected)

        coordinator.onAction(PanelAction.FINISH_TRIP)

        assertEquals(emptyList<String>(), commands.calls)
    }

    @Test
    fun `open app runs and collapses`() {
        coordinator.onShiftState(connected)
        coordinator.onBubbleTapped()

        coordinator.onAction(PanelAction.OPEN_APP)

        assertEquals(listOf("open"), commands.calls)
        assertEquals(false, ui.expanded)
    }

    @Test
    fun `asking to cancel only shows the confirmation`() {
        coordinator.onShiftState(tripA)
        coordinator.onBubbleTapped()

        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        assertEquals(emptyList<String>(), commands.calls)
        assertEquals(true, ui.panel!!.confirmingCancel)
        assertEquals(true, ui.expanded)
    }

    @Test
    fun `confirming cancels the ride it was asked for and collapses`() {
        coordinator.onShiftState(tripA)
        coordinator.onBubbleTapped()
        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        coordinator.onAction(PanelAction.CONFIRM_CANCEL_TRIP)

        assertEquals(listOf("cancel:10"), commands.calls)
        assertEquals(false, ui.expanded)
        assertEquals(false, ui.panel!!.confirmingCancel)
    }

    @Test
    fun `confirming without asking first does nothing`() {
        coordinator.onShiftState(tripA)

        coordinator.onAction(PanelAction.CONFIRM_CANCEL_TRIP)

        assertEquals(emptyList<String>(), commands.calls)
    }

    @Test
    fun `back keeps the ride and returns to the normal actions`() {
        coordinator.onShiftState(tripA)
        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        coordinator.onAction(PanelAction.KEEP_TRIP)

        assertEquals(emptyList<String>(), commands.calls)
        assertEquals(false, ui.panel!!.confirmingCancel)
    }

    @Test
    fun `collapsing resets a pending confirmation`() {
        coordinator.onShiftState(tripA)
        coordinator.onBubbleTapped()
        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        coordinator.onCollapse()
        coordinator.onBubbleTapped()

        assertEquals(false, ui.panel!!.confirmingCancel)
    }

    @Test
    fun `the confirmation resets when the ride ends`() {
        coordinator.onShiftState(tripA)
        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        coordinator.onShiftState(connected)
        coordinator.onShiftState(tripA)

        assertEquals(false, ui.panel!!.confirmingCancel)
    }

    @Test
    fun `the confirmation resets when a different ride starts and never cancels it`() {
        coordinator.onShiftState(tripA)
        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        coordinator.onShiftState(tripB)
        coordinator.onAction(PanelAction.CONFIRM_CANCEL_TRIP)

        assertEquals(emptyList<String>(), commands.calls)
    }

    @Test
    fun `the confirmation survives updates of the same ride`() {
        coordinator.onShiftState(tripA)
        coordinator.onAction(PanelAction.ASK_CANCEL_TRIP)

        coordinator.onShiftState(tripA.copy())

        assertEquals(true, ui.panel!!.confirmingCancel)
    }

    @Test
    fun `the panel collapses when the shift becomes disconnected`() {
        coordinator.onShiftState(connected)
        coordinator.onBubbleTapped()

        coordinator.onShiftState(ShiftState.Disconnected)

        assertEquals(false, ui.expanded)
        assertNull(ui.panel)
    }
}
