package com.micuadre.app.presentation

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeDialogCoordinatorTest {

    private val ui = MutableStateFlow(HomeUiState())
    private var endShifts = 0
    private val coordinator = HomeDialogCoordinator(ui = ui, endShift = { endShifts++ })

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

        assertEquals(0, endShifts)
    }
}
