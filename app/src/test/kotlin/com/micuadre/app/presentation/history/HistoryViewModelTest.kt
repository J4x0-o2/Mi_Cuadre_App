package com.micuadre.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.usecase.ObserveMonthHistoryUseCase
import com.micuadre.app.domain.usecase.ObserveYearHistoryUseCase
import com.micuadre.app.util.FakeClock
import com.micuadre.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val clock = FakeClock(msOf("2026-10-18", "15:30:00"))
    private val shifts = FakeHistoryShifts(
        listOf(
            shiftOn(1, "2025-11-20", 40_000),
            shiftOn(2, "2026-09-10", 20_000),
            shiftOn(3, "2026-10-02", 8_000)
        )
    )
    private val amounts =
        listOf(SessionDeductionAmount(sessionId = 2, deductionId = 1, name = "Fuel", amountMinor = 5_000))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()): HistoryViewModel {
        val year = ObserveYearHistoryUseCase(shifts, FakeHistoryDeductions(amounts), clock)
        val month = ObserveMonthHistoryUseCase(shifts, FakeHistoryDeductions(amounts), clock)
        return HistoryViewModel(
            savedState = handle,
            observeYear = year::invoke,
            observeMonth = month::invoke,
            clock = clock,
            zone = bogota,
            clockChanges = MutableSharedFlow()
        )
    }

    /** Keeps the WhileSubscribed state flows hot for the duration of a test. */
    private fun TestScope.collectAll(vm: HistoryViewModel) {
        vm.year.onEach { }.launchIn(backgroundScope)
        vm.month.onEach { }.launchIn(backgroundScope)
        runCurrent()
    }

    @Test
    fun `opens on the current year`() = runTest {
        val vm = viewModel()
        collectAll(vm)

        assertEquals(2026, vm.year.value?.year)
        assertEquals(listOf("Octubre", "Septiembre"), vm.year.value?.months?.map { it.name })
        assertTrue(vm.year.value!!.months.first().isCurrent)
    }

    @Test
    fun `stepping back shows the previous year and stepping forward returns`() = runTest {
        val vm = viewModel()
        collectAll(vm)

        vm.onPreviousYear()
        assertEquals(2025, vm.year.value?.year)
        assertEquals(listOf("Noviembre"), vm.year.value?.months?.map { it.name })

        vm.onNextYear()
        assertEquals(2026, vm.year.value?.year)
    }

    @Test
    fun `cannot step before the first year or past the current one`() = runTest {
        val vm = viewModel()
        collectAll(vm)

        vm.onNextYear()
        assertEquals(2026, vm.year.value?.year)

        vm.onPreviousYear()
        vm.onPreviousYear()
        assertEquals(2025, vm.year.value?.year)
        assertFalse(vm.year.value!!.canGoBack)
    }

    @Test
    fun `selecting a month loads its detail`() = runTest {
        val vm = viewModel()
        collectAll(vm)
        assertNull(vm.month.value)

        vm.onMonthSelected(YearMonth.of(2026, 9))

        assertEquals("Septiembre", vm.month.value?.title)
        assertEquals(Formatters.expenseMoney(5_000), vm.month.value?.expensesText)
    }

    @Test
    fun `the selected year and month survive a recreated view model`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(handle)
        collectAll(first)
        first.onPreviousYear()
        first.onMonthSelected(YearMonth.of(2025, 11))

        val restored = viewModel(handle)
        collectAll(restored)

        assertEquals(2025, restored.year.value?.year)
        assertEquals("Noviembre", restored.month.value?.title)
    }

    @Test
    fun `the current month flag flips when the day crosses a month boundary`() = runTest {
        clock.now = msOf("2026-10-31", "23:59:30")
        val vm = viewModel()
        collectAll(vm)
        assertTrue(vm.year.value!!.months.first().isCurrent)

        clock.now = msOf("2026-11-01", "00:00:01")
        advanceTimeBy(31_000L)
        runCurrent()

        assertFalse(vm.year.value!!.months.first().isCurrent)
    }
}
