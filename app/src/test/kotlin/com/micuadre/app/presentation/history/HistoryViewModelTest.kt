package com.micuadre.app.presentation.history

import androidx.lifecycle.SavedStateHandle
import com.micuadre.app.domain.model.MonthDetail
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.usecase.ObserveMonthHistoryUseCase
import com.micuadre.app.domain.usecase.ObserveYearHistoryUseCase
import com.micuadre.app.util.FakeClock
import com.micuadre.app.util.Formatters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
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
import java.time.ZoneId

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

    private val yearUseCase = ObserveYearHistoryUseCase(shifts, FakeHistoryDeductions(amounts), clock)
    private val monthUseCase = ObserveMonthHistoryUseCase(shifts, FakeHistoryDeductions(amounts), clock)

    private var zone: ZoneId = bogota
    private val clockChanges = MutableSharedFlow<Unit>()

    private fun viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        observeMonth: (YearMonth, ZoneId) -> Flow<MonthDetail> = monthUseCase::invoke
    ): HistoryViewModel = HistoryViewModel(
        savedState = handle,
        observeYear = yearUseCase::invoke,
        observeMonth = observeMonth,
        clock = clock,
        zone = { zone },
        clockChanges = clockChanges
    )

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

    @Test
    fun `a new month selection clears the previous month until its first read`() = runTest {
        val pending = MutableSharedFlow<MonthDetail>()
        val vm = viewModel { month, zone ->
            if (month == YearMonth.of(2026, 9)) monthUseCase(month, zone) else pending
        }
        collectAll(vm)

        vm.onMonthSelected(YearMonth.of(2026, 9))
        assertEquals("Septiembre", vm.month.value?.title)

        vm.onMonthSelected(YearMonth.of(2026, 10))
        assertNull(vm.month.value)

        pending.emit(monthUseCase(YearMonth.of(2026, 10), bogota).first())
        assertEquals("Octubre", vm.month.value?.title)
    }

    @Test
    fun `a time zone change re-subscribes with the new zone`() = runTest {
        val zones = mutableListOf<ZoneId>()
        val vm = viewModel { month, zone ->
            zones += zone
            monthUseCase(month, zone)
        }
        collectAll(vm)
        vm.onMonthSelected(YearMonth.of(2026, 9))
        assertEquals(listOf(bogota), zones)

        zone = ZoneId.of("Asia/Tokyo")
        clockChanges.emit(Unit)
        runCurrent()

        assertEquals(listOf(bogota, ZoneId.of("Asia/Tokyo")), zones)
    }
}
