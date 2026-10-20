package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDialogCoordinatorTest {

    private val ui = MutableStateFlow(HomeUiState())
    private var endShifts = 0
    private var cancels = 0
    private val cancelledTrips = mutableListOf<Long>()
    private val coordinator = HomeDialogCoordinator(
        ui = ui,
        endShift = { endShifts++ },
        cancelTrip = { tripStartedAtMs ->
            cancels++
            cancelledTrips += tripStartedAtMs
        }
    )

    private fun onTrip(startedAtMs: Long = TRIP_A) {
        ui.value = ui.value.copy(shiftState = ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = startedAtMs))
    }

    @Test
    fun `tapping end shift opens the confirmation without ending the shift`() {
        coordinator.onEndShiftTapped()

        assertEquals(HomeDialog.EndShift, ui.value.dialog)
        assertEquals(0, endShifts)
    }

    @Test
    fun `confirming ends the shift once and closes the dialog`() {
        coordinator.onEndShiftTapped()

        coordinator.onEndShiftConfirmed()
        coordinator.onEndShiftConfirmed()

        assertEquals(1, endShifts)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `dismissing keeps the shift and closes the dialog`() {
        coordinator.onEndShiftTapped()

        coordinator.onDialogDismissed()

        assertEquals(0, endShifts)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `confirming without an open dialog does nothing`() {
        coordinator.onEndShiftConfirmed()
        coordinator.onCancelTripConfirmed()

        assertEquals(0, endShifts)
        assertEquals(0, cancels)
    }

    @Test
    fun `tapping cancel ride opens the confirmation without cancelling`() {
        onTrip()

        coordinator.onCancelTripTapped()

        assertEquals(HomeDialog.CancelTrip(TRIP_A), ui.value.dialog)
        assertEquals(0, cancels)
    }

    @Test
    fun `confirming cancels the ride once and closes the dialog`() {
        onTrip()
        coordinator.onCancelTripTapped()

        coordinator.onCancelTripConfirmed()
        coordinator.onCancelTripConfirmed()

        assertEquals(listOf(TRIP_A), cancelledTrips)
        assertEquals(1, cancels)
        assertEquals(0, endShifts)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `dismissing the cancel dialog keeps the ride`() {
        onTrip()
        coordinator.onCancelTripTapped()

        coordinator.onDialogDismissed()

        assertEquals(0, cancels)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `confirming end shift does not run while the cancel dialog is open`() {
        onTrip()
        coordinator.onCancelTripTapped()

        coordinator.onEndShiftConfirmed()

        assertEquals(0, endShifts)
        assertEquals(HomeDialog.CancelTrip(TRIP_A), ui.value.dialog)
    }

    @Test
    fun `cancel ride cannot be opened while not on a trip`() {
        ui.value = ui.value.copy(shiftState = ShiftState.Connected(connectedSinceMs = 1L))

        coordinator.onCancelTripTapped()

        assertNull(ui.value.dialog)
    }

    @Test
    fun `the cancel dialog closes when the shift leaves the trip`() {
        onTrip()
        coordinator.onCancelTripTapped()

        coordinator.onShiftState(ShiftState.Connected(connectedSinceMs = 1L))

        assertNull(ui.value.dialog)
        coordinator.onCancelTripConfirmed()
        assertEquals(0, cancels)
    }

    @Test
    fun `the cancel dialog closes when a different ride starts`() {
        onTrip(TRIP_A)
        coordinator.onCancelTripTapped()

        coordinator.onShiftState(ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = TRIP_B))

        assertNull(ui.value.dialog)
    }

    @Test
    fun `the cancel dialog stays open while the same ride continues`() {
        onTrip(TRIP_A)
        coordinator.onCancelTripTapped()

        coordinator.onShiftState(ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = TRIP_A))

        assertEquals(HomeDialog.CancelTrip(TRIP_A), ui.value.dialog)
    }

    @Test
    fun `state changes leave the end shift dialog alone`() {
        coordinator.onEndShiftTapped()

        coordinator.onShiftState(ShiftState.Connected(connectedSinceMs = 1L))

        assertEquals(HomeDialog.EndShift, ui.value.dialog)
    }

    private companion object {
        const val TRIP_A = 5_000L
        const val TRIP_B = 9_000L
    }
}
