package com.micuadre.app.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDialogCoordinatorTest {

    private val ui = MutableStateFlow(HomeUiState())
    private var endShifts = 0
    private var cancels = 0
    private val coordinator = HomeDialogCoordinator(
        ui = ui,
        endShift = { endShifts++ },
        cancelTrip = { cancels++ }
    )

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
        coordinator.onCancelTripTapped()

        assertEquals(HomeDialog.CancelTrip, ui.value.dialog)
        assertEquals(0, cancels)
    }

    @Test
    fun `confirming cancels the ride once and closes the dialog`() {
        coordinator.onCancelTripTapped()

        coordinator.onCancelTripConfirmed()
        coordinator.onCancelTripConfirmed()

        assertEquals(1, cancels)
        assertEquals(0, endShifts)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `dismissing the cancel dialog keeps the ride`() {
        coordinator.onCancelTripTapped()

        coordinator.onDialogDismissed()

        assertEquals(0, cancels)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `confirming end shift does not run while the cancel dialog is open`() {
        coordinator.onCancelTripTapped()

        coordinator.onEndShiftConfirmed()

        assertEquals(0, endShifts)
        assertEquals(HomeDialog.CancelTrip, ui.value.dialog)
    }
}
