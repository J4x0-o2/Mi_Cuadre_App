package com.micuadre.app.presentation

import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.presentation.expenses.ExpenseField
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDialogCoordinatorTest {

    private val ui = MutableStateFlow(HomeUiState(hasOpenShift = true))
    private var endShifts = 0
    private val endedWith = mutableListOf<Map<Long, Long>>()
    private var visible: List<Deduction> = emptyList()
    private var lookups = 0
    private var lookupGate: CompletableDeferred<Unit>? = null
    private var lookupFailure: Throwable? = null
    private var cancels = 0
    private val cancelledTrips = mutableListOf<Long>()
    private val coordinator = HomeDialogCoordinator(
        ui = ui,
        scope = TestScope(UnconfinedTestDispatcher()),
        visibleDeductions = {
            lookups++
            lookupGate?.await()
            lookupFailure?.let { throw it }
            visible
        },
        endShift = { amounts ->
            endShifts++
            endedWith += amounts
        },
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

        coordinator.onShiftState(ShiftState.Connected(connectedSinceMs = 1L), hasOpenShift = true)

        assertNull(ui.value.dialog)
        coordinator.onCancelTripConfirmed()
        assertEquals(0, cancels)
    }

    @Test
    fun `the cancel dialog closes when a different ride starts`() {
        onTrip(TRIP_A)
        coordinator.onCancelTripTapped()

        coordinator.onShiftState(ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = TRIP_B), hasOpenShift = true)

        assertNull(ui.value.dialog)
    }

    @Test
    fun `the cancel dialog stays open while the same ride continues`() {
        onTrip(TRIP_A)
        coordinator.onCancelTripTapped()

        coordinator.onShiftState(ShiftState.OnTrip(connectedSinceMs = 1L, tripStartedAtMs = TRIP_A), hasOpenShift = true)

        assertEquals(HomeDialog.CancelTrip(TRIP_A), ui.value.dialog)
    }

    @Test
    fun `state changes leave the end shift dialog alone`() {
        coordinator.onEndShiftTapped()

        coordinator.onShiftState(ShiftState.Connected(connectedSinceMs = 1L), hasOpenShift = true)

        assertEquals(HomeDialog.EndShift, ui.value.dialog)
    }

    private val fuel = Deduction(id = 1L, name = "Gasolina", usualAmountMinor = 1_500_000L)
    private val food = Deduction(id = 2L, name = "Almuerzo")

    private fun confirmWith(vararg deductions: Deduction) {
        visible = deductions.toList()
        coordinator.onEndShiftTapped()
        coordinator.onEndShiftConfirmed()
    }

    @Test
    fun `confirming with no visible deductions ends the shift at once without a sheet`() {
        confirmWith()

        assertEquals(listOf(emptyMap<Long, Long>()), endedWith)
        assertNull(ui.value.shiftExpenses)
        assertNull(ui.value.dialog)
    }

    @Test
    fun `confirming with visible deductions opens the prefilled sheet and keeps the shift open`() {
        confirmWith(fuel, food)

        assertEquals(0, endShifts)
        assertNull(ui.value.dialog)
        assertEquals(
            listOf(ExpenseField(1L, "Gasolina", "15.000"), ExpenseField(2L, "Almuerzo", "")),
            ui.value.shiftExpenses?.fields
        )
    }

    @Test
    fun `skipping ends the shift with no amounts and closes the sheet`() {
        confirmWith(fuel, food)

        coordinator.onShiftExpensesSkipped()

        assertEquals(listOf(emptyMap<Long, Long>()), endedWith)
        assertNull(ui.value.shiftExpenses)
    }

    @Test
    fun `saving sends the typed amounts in minor units and drops blanks`() {
        confirmWith(fuel, food)
        coordinator.onShiftExpensesEdited(1L, "")
        coordinator.onShiftExpensesEdited(2L, "8500")

        coordinator.onShiftExpensesSaved()

        assertEquals(listOf(mapOf(2L to 850_000L)), endedWith)
        assertNull(ui.value.shiftExpenses)
    }

    @Test
    fun `saving prefilled usual amounts sends them untouched`() {
        confirmWith(fuel, food)

        coordinator.onShiftExpensesSaved()

        assertEquals(listOf(mapOf(1L to 1_500_000L)), endedWith)
    }

    @Test
    fun `a double tap on save and skip together sends a single end action`() {
        confirmWith(fuel)

        coordinator.onShiftExpensesSaved()
        coordinator.onShiftExpensesSaved()
        coordinator.onShiftExpensesSkipped()

        assertEquals(1, endShifts)
    }

    @Test
    fun `dismissing the sheet cancels and the shift stays open`() {
        confirmWith(fuel)

        coordinator.onShiftExpensesDismissed()
        coordinator.onShiftExpensesSaved()
        coordinator.onShiftExpensesSkipped()

        assertEquals(0, endShifts)
        assertNull(ui.value.shiftExpenses)
    }

    @Test
    fun `editing without an open sheet does nothing`() {
        coordinator.onShiftExpensesEdited(1L, "5")

        assertNull(ui.value.shiftExpenses)
    }

    @Test
    fun `repeated confirms while the deductions load open one sheet and look up once`() {
        visible = listOf(fuel)
        lookupGate = CompletableDeferred()
        coordinator.onEndShiftTapped()
        coordinator.onEndShiftConfirmed()
        coordinator.onEndShiftTapped()
        coordinator.onEndShiftConfirmed()

        lookupGate?.complete(Unit)

        assertEquals(1, lookups)
        assertEquals(0, endShifts)
        assertEquals(1, ui.value.shiftExpenses?.fields?.size)
    }

    @Test
    fun `a failing lookup still ends the shift with no amounts`() {
        lookupFailure = IOException("disk")

        confirmWith(fuel)

        assertEquals(listOf(emptyMap<Long, Long>()), endedWith)
        assertNull(ui.value.shiftExpenses)
    }


    @Test
    fun `a lookup that completes after the shift ended opens no sheet and ends nothing`() {
        visible = listOf(fuel)
        lookupGate = CompletableDeferred()
        coordinator.onEndShiftTapped()
        coordinator.onEndShiftConfirmed()

        ui.value = ui.value.copy(hasOpenShift = false, shiftState = ShiftState.Disconnected)
        lookupGate?.complete(Unit)

        assertNull(ui.value.shiftExpenses)
        assertEquals(0, endShifts)
    }

    @Test
    fun `a lookup with no deductions that completes after the shift ended sends no end`() {
        lookupGate = CompletableDeferred()
        coordinator.onEndShiftTapped()
        coordinator.onEndShiftConfirmed()

        ui.value = ui.value.copy(hasOpenShift = false)
        lookupGate?.complete(Unit)

        assertEquals(0, endShifts)
    }

    @Test
    fun `the sheet closes when the shift ends while it is open`() {
        confirmWith(fuel)
        ui.value = ui.value.copy(hasOpenShift = false)

        coordinator.onShiftState(ShiftState.Disconnected, hasOpenShift = false)
        coordinator.onShiftExpensesSaved()

        assertNull(ui.value.shiftExpenses)
        assertEquals(0, endShifts)
    }

    @Test
    fun `the sheet closes from the published openness even if the stored flag is not refreshed yet`() {
        confirmWith(fuel)
        // The caller has not copied the new snapshot into the UI state yet.
        assertEquals(true, ui.value.hasOpenShift)

        coordinator.onShiftState(ShiftState.Disconnected, hasOpenShift = false)

        assertNull(ui.value.shiftExpenses)
    }

    @Test
    fun `the sheet stays open while the shift is paused`() {
        confirmWith(fuel)
        ui.value = ui.value.copy(shiftState = ShiftState.Disconnected, hasOpenShift = true)

        coordinator.onShiftState(ShiftState.Disconnected, hasOpenShift = true)

        assertEquals(1, ui.value.shiftExpenses?.fields?.size)
    }

    private companion object {
        const val TRIP_A = 5_000L
        const val TRIP_B = 9_000L
    }
}
