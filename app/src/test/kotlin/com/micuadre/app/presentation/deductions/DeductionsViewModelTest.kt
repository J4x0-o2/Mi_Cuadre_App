package com.micuadre.app.presentation.deductions

import com.micuadre.app.R
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DeductionsViewModelTest {

    /** Scripted repository: records calls and answers with [next] (or throws [failure]). */
    private class FakeRepo(initial: List<Deduction> = emptyList()) : DeductionRepository {
        val deductions = MutableStateFlow(initial)
        var next: DeductionResult = DeductionResult.Ok
        var failure: Throwable? = null
        val calls = mutableListOf<String>()

        override fun observeActive(): Flow<List<Deduction>> = deductions
        override fun observeVisible(): Flow<List<Deduction>> = deductions

        override suspend fun create(name: String, usualAmountMinor: Long?) = answer("create:$name:$usualAmountMinor")
        override suspend fun rename(id: Long, name: String) = answer("rename:$id:$name")
        override suspend fun setVisible(id: Long, visible: Boolean) = answer("visible:$id:$visible")
        override suspend fun setUsualAmount(id: Long, amountMinor: Long?) = answer("usual:$id:$amountMinor")
        override suspend fun delete(id: Long) = answer("delete:$id")
        override suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>) = answer("save")
        override suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount> = emptyList()
        override fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>> =
            MutableStateFlow(emptyList())

        private fun answer(call: String): DeductionResult {
            calls += call
            failure?.let { throw it }
            return next
        }
    }

    private val fuel = Deduction(id = 1, name = "Combustible", usualAmountMinor = 1_500_000L)
    private val food = Deduction(id = 2, name = "Alimentación", visible = false)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exposes the active deductions once loaded`() = runTest {
        val vm = DeductionsViewModel(FakeRepo(listOf(fuel, food)))

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(fuel, food), state.deductions)
        assertNull(state.dialog)
    }

    @Test
    fun `starts loading before the first emission`() {
        assertTrue(DeductionsUiState().isLoading)
    }

    @Test
    fun `toggling visibility writes to the repository`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)

        vm.onToggleVisible(fuel.id, false)

        assertEquals(listOf("visible:1:false"), repo.calls)
    }

    @Test
    fun `add dialog opens and closes`() = runTest {
        val vm = DeductionsViewModel(FakeRepo())

        vm.onAddClick()
        assertEquals(DeductionDialog.Add(), vm.state.value.dialog)

        vm.onDismissDialog()
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `adding without an amount creates with no usual amount and closes`() = runTest {
        val repo = FakeRepo()
        val vm = DeductionsViewModel(repo)
        vm.onAddClick()

        vm.onConfirmAdd("Peajes", "")

        assertEquals(listOf("create:Peajes:null"), repo.calls)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `adding with an amount converts pesos to minor units`() = runTest {
        val repo = FakeRepo()
        val vm = DeductionsViewModel(repo)
        vm.onAddClick()

        vm.onConfirmAdd("Peajes", "15.000")

        assertEquals(listOf("create:Peajes:1500000"), repo.calls)
    }

    @Test
    fun `adding with a zero amount stores no usual amount`() = runTest {
        val repo = FakeRepo()
        val vm = DeductionsViewModel(repo)
        vm.onAddClick()

        vm.onConfirmAdd("Peajes", "0")

        assertEquals(listOf("create:Peajes:null"), repo.calls)
    }

    @Test
    fun `invalid names keep the add dialog open with an inline error`() = runTest {
        val repo = FakeRepo()
        val vm = DeductionsViewModel(repo)
        vm.onAddClick()

        repo.next = DeductionResult.Blank
        vm.onConfirmAdd(" ", "")
        assertEquals(DeductionDialog.Add(DeductionFormError.Blank), vm.state.value.dialog)

        repo.next = DeductionResult.Duplicate
        vm.onConfirmAdd("combustible", "")
        assertEquals(DeductionDialog.Add(DeductionFormError.Duplicate), vm.state.value.dialog)

        repo.next = DeductionResult.TooLong
        vm.onConfirmAdd("x".repeat(31), "")
        assertEquals(DeductionDialog.Add(DeductionFormError.TooLong), vm.state.value.dialog)

        repo.next = DeductionResult.InvalidAmount
        vm.onConfirmAdd("Peajes", "5")
        assertEquals(DeductionDialog.Add(DeductionFormError.InvalidAmount), vm.state.value.dialog)
    }

    @Test
    fun `editing the form clears the inline error`() = runTest {
        val repo = FakeRepo()
        val vm = DeductionsViewModel(repo)
        vm.onAddClick()
        repo.next = DeductionResult.Duplicate
        vm.onConfirmAdd("combustible", "")

        vm.onFormEdited()

        assertEquals(DeductionDialog.Add(), vm.state.value.dialog)
    }

    @Test
    fun `edit dialog carries the current name and usual amount`() = runTest {
        val vm = DeductionsViewModel(FakeRepo(listOf(fuel, food)))

        vm.onEditClick(fuel.id)
        assertEquals(
            DeductionDialog.Edit(id = 1, currentName = "Combustible", currentAmount = "15.000"),
            vm.state.value.dialog
        )

        vm.onEditClick(food.id)
        assertEquals(
            DeductionDialog.Edit(id = 2, currentName = "Alimentación", currentAmount = ""),
            vm.state.value.dialog
        )
    }

    @Test
    fun `edit click for an unknown id opens nothing`() = runTest {
        val vm = DeductionsViewModel(FakeRepo(listOf(fuel)))

        vm.onEditClick(99L)

        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `confirming an edit renames then sets the usual amount`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        vm.onEditClick(fuel.id)

        vm.onConfirmEdit("Gasolina", "20.000")

        assertEquals(listOf("rename:1:Gasolina", "usual:1:2000000"), repo.calls)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `clearing the amount in an edit clears the usual amount`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        vm.onEditClick(fuel.id)

        vm.onConfirmEdit("Combustible", "")

        assertEquals(listOf("rename:1:Combustible", "usual:1:null"), repo.calls)
    }

    @Test
    fun `a refused rename keeps the edit dialog open and skips the amount`() = runTest {
        val repo = FakeRepo(listOf(fuel, food))
        val vm = DeductionsViewModel(repo)
        vm.onEditClick(food.id)
        repo.next = DeductionResult.Duplicate

        vm.onConfirmEdit("combustible", "5.000")

        assertEquals(listOf("rename:2:combustible"), repo.calls)
        assertEquals(
            DeductionDialog.Edit(
                id = 2,
                currentName = "Alimentación",
                currentAmount = "",
                error = DeductionFormError.Duplicate
            ),
            vm.state.value.dialog
        )
    }

    @Test
    fun `an invalid amount in an edit keeps the dialog open`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        vm.onEditClick(fuel.id)
        repo.next = DeductionResult.InvalidAmount

        vm.onConfirmEdit("Combustible", "5")

        assertEquals(DeductionFormError.InvalidAmount, (vm.state.value.dialog as DeductionDialog.Edit).error)
    }

    @Test
    fun `editing a deduction that vanished closes the dialog`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        vm.onEditClick(fuel.id)
        repo.next = DeductionResult.NotFound

        vm.onConfirmEdit("Gasolina", "")

        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `delete asks for confirmation before touching the repository`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)

        vm.onDeleteClick(fuel.id)
        assertEquals(DeductionDialog.Delete(id = 1, name = "Combustible"), vm.state.value.dialog)
        assertTrue(repo.calls.isEmpty())

        vm.onConfirmDelete()
        assertEquals(listOf("delete:1"), repo.calls)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `cancelling the delete dialog deletes nothing`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        vm.onDeleteClick(fuel.id)

        vm.onDismissDialog()

        assertTrue(repo.calls.isEmpty())
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `write failure posts a message and closes the dialog`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        repo.failure = IOException("disk full")
        vm.onAddClick()

        vm.onConfirmAdd("Peajes", "")

        assertEquals(R.string.deductions_save_error, vm.message.value?.textRes)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `failed toggle posts distinct messages and shown clears only the matching id`() = runTest {
        val repo = FakeRepo(listOf(fuel))
        val vm = DeductionsViewModel(repo)
        repo.failure = IOException("locked")

        vm.onToggleVisible(fuel.id, false)
        val first = vm.message.value!!
        vm.onToggleVisible(fuel.id, false)
        val second = vm.message.value!!

        assertNotEquals(first.id, second.id)
        vm.onMessageShown(first.id)
        assertEquals(second, vm.message.value)
        vm.onMessageShown(second.id)
        assertNull(vm.message.value)
    }

    @Test
    fun `secondary line shows the usual amount, none, or hidden`() {
        assertEquals(DeductionSecondary.Usual(1_500_000L), fuel.secondaryLine())
        assertEquals(DeductionSecondary.NoUsual, Deduction(id = 3, name = "Peajes").secondaryLine())
        assertEquals(DeductionSecondary.Hidden, food.secondaryLine())
        // Hidden wins over a usual amount: the row says it is not asked.
        assertEquals(DeductionSecondary.Hidden, fuel.copy(visible = false).secondaryLine())
    }
}
