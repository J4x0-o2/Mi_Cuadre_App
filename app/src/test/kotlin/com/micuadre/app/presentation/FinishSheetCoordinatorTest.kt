package com.micuadre.app.presentation

import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformSelection
import com.micuadre.app.domain.model.ShiftState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FinishSheetCoordinatorTest {

    private val connected = ShiftState.Connected(connectedSinceMs = 1_000L)
    private val onTrip = ShiftState.OnTrip(connectedSinceMs = 1_000L, tripStartedAtMs = 5_000L)
    private val selection = PlatformSelection(Platform.DIDI)

    private class Fixture(
        scope: TestScope,
        initial: ShiftState,
        val lookup: suspend () -> PlatformSelection
    ) {
        val ui = MutableStateFlow(HomeUiState(shiftState = initial))
        var lookups = 0
        val coordinator = FinishSheetCoordinator(
            ui = ui,
            scope = scope,
            lastUsedPlatform = { lookups++; lookup() },
            nowMs = { 9_000L }
        )

        /** Mirrors what the view model does when the snapshot flow emits. */
        fun emit(state: ShiftState) {
            ui.value = ui.value.copy(shiftState = state)
            coordinator.onShiftState(state)
        }
    }

    private fun TestScope.fixture(initial: ShiftState, lookup: suspend () -> PlatformSelection = { selection }) =
        Fixture(this, initial, lookup)

    @Test
    fun `request while on a trip opens the populated sheet`() = runTest {
        val f = fixture(onTrip)

        f.coordinator.onLaunchRequest(LaunchRequest.OPEN_FINISH_SHEET)
        advanceUntilIdle()

        val sheet = f.ui.value.finishSheet
        assertNotNull(sheet)
        assertEquals(5_000L, sheet!!.tripStartedAtMs)
        assertEquals(9_000L, sheet.tripEndedAtMs)
        assertEquals(selection, sheet.initialSelection)
    }

    @Test
    fun `request before the state is restored waits and opens once on trip`() = runTest {
        val f = fixture(ShiftState.Disconnected)

        f.coordinator.onLaunchRequest(LaunchRequest.OPEN_FINISH_SHEET)
        runCurrent()
        assertNull(f.ui.value.finishSheet)

        f.emit(onTrip)
        advanceUntilIdle()

        assertNotNull(f.ui.value.finishSheet)
        assertEquals(1, f.lookups)
    }

    @Test
    fun `request expires if the state never shows a trip`() = runTest {
        val f = fixture(ShiftState.Disconnected)

        f.coordinator.onLaunchRequest(LaunchRequest.OPEN_FINISH_SHEET)
        advanceTimeBy(FinishSheetCoordinator.PENDING_TIMEOUT_MS + 1)
        runCurrent()
        f.emit(onTrip)
        advanceUntilIdle()

        assertNull(f.ui.value.finishSheet)
    }

    @Test
    fun `stale request on a connected shift is dropped`() = runTest {
        val f = fixture(connected)

        f.coordinator.onLaunchRequest(LaunchRequest.OPEN_FINISH_SHEET)
        advanceUntilIdle()
        f.emit(onTrip)
        advanceUntilIdle()

        assertNull(f.ui.value.finishSheet)
    }

    @Test
    fun `tapping finish while connected does nothing`() = runTest {
        val f = fixture(connected)

        f.coordinator.onFinishTapped()
        advanceUntilIdle()

        assertNull(f.ui.value.finishSheet)
        assertEquals(0, f.lookups)
    }

    @Test
    fun `repeated triggers during the lookup perform a single lookup`() = runTest {
        val gate = CompletableDeferred<PlatformSelection>()
        val f = fixture(onTrip) { gate.await() }

        f.coordinator.onFinishTapped()
        f.coordinator.onFinishTapped()
        f.coordinator.onLaunchRequest(LaunchRequest.OPEN_FINISH_SHEET)
        runCurrent()
        gate.complete(selection)
        advanceUntilIdle()

        assertEquals(1, f.lookups)
        assertNotNull(f.ui.value.finishSheet)
    }

    @Test
    fun `dismissing during the lookup does not reopen the sheet`() = runTest {
        val gate = CompletableDeferred<PlatformSelection>()
        val f = fixture(onTrip) { gate.await() }

        f.coordinator.onFinishTapped()
        runCurrent()
        f.coordinator.onDismissed()
        gate.complete(selection)
        advanceUntilIdle()

        assertNull(f.ui.value.finishSheet)
    }

    @Test
    fun `trip finishing during the lookup does not open the sheet`() = runTest {
        val gate = CompletableDeferred<PlatformSelection>()
        val f = fixture(onTrip) { gate.await() }

        f.coordinator.onFinishTapped()
        runCurrent()
        f.emit(connected)
        gate.complete(selection)
        advanceUntilIdle()

        assertNull(f.ui.value.finishSheet)
    }

    @Test
    fun `an already open sheet is never replaced`() = runTest {
        val f = fixture(onTrip)
        f.coordinator.onFinishTapped()
        advanceUntilIdle()
        val first = f.ui.value.finishSheet

        f.coordinator.onFinishTapped()
        advanceUntilIdle()

        assertEquals(first, f.ui.value.finishSheet)
        assertEquals(1, f.lookups)
    }
}
