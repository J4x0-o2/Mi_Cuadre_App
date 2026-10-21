package com.micuadre.app.presentation.platforms

import com.micuadre.app.R
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.repository.PlatformRepository
import com.micuadre.app.domain.repository.PlatformResult
import kotlinx.coroutines.CompletableDeferred
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
class PlatformsViewModelTest {

    /** Scripted repository: records calls and answers with [next] (or throws [failure]). */
    private class FakeRepo(initial: List<Platform> = emptyList()) : PlatformRepository {
        val platforms = MutableStateFlow(initial)
        var next: PlatformResult = PlatformResult.Ok
        var failure: Throwable? = null
        val calls = mutableListOf<String>()

        /** When set, every write suspends until it completes, to model a write in flight. */
        var gate: CompletableDeferred<Unit>? = null

        override fun observeActive(): Flow<List<Platform>> = platforms

        override suspend fun create(name: String): PlatformResult = answer("create:$name")
        override suspend fun rename(id: Long, name: String): PlatformResult = answer("rename:$id:$name")
        override suspend fun setVisible(id: Long, visible: Boolean): PlatformResult = answer("visible:$id:$visible")
        override suspend fun delete(id: Long): PlatformResult = answer("delete:$id")

        private suspend fun answer(call: String): PlatformResult {
            calls += call
            gate?.await()
            failure?.let { throw it }
            return next
        }
    }

    private val beat = Platform(id = 1, name = "Beat")
    private val uber = Platform(id = 2, name = "Uber", visible = false)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `exposes the active platforms once loaded`() = runTest {
        val vm = PlatformsViewModel(FakeRepo(listOf(beat, uber)))

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(listOf(beat, uber), state.platforms)
        assertNull(state.dialog)
    }

    @Test
    fun `no hint when at least one platform is visible`() = runTest {
        val vm = PlatformsViewModel(FakeRepo(listOf(beat, uber)))

        assertFalse(vm.state.value.showNoneVisibleHint)
    }

    @Test
    fun `a double confirm writes once and closes the dialog`() = runTest {
        val repo = FakeRepo()
        val vm = PlatformsViewModel(repo)
        vm.onAddClick()
        repo.gate = CompletableDeferred()

        vm.onConfirmAdd("Didi")
        assertTrue(vm.state.value.saving)
        vm.onConfirmAdd("Didi")
        repo.gate!!.complete(Unit)

        assertEquals(listOf("create:Didi"), repo.calls)
        assertNull(vm.state.value.dialog)
        assertFalse(vm.state.value.saving)
    }

    @Test
    fun `dismissing during a write keeps the dialog dismissed`() = runTest {
        val repo = FakeRepo()
        val vm = PlatformsViewModel(repo)
        vm.onAddClick()
        repo.gate = CompletableDeferred()
        vm.onConfirmAdd("Didi")

        vm.onDismissDialog()
        repo.next = PlatformResult.Duplicate
        repo.gate!!.complete(Unit)

        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `no hint while loading`() {
        assertFalse(PlatformsUiState().showNoneVisibleHint)
    }

    @Test
    fun `hint shows when every platform is hidden or the list is empty`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)

        repo.platforms.value = listOf(beat.copy(visible = false), uber)
        assertTrue(vm.state.value.showNoneVisibleHint)

        repo.platforms.value = emptyList()
        assertTrue(vm.state.value.showNoneVisibleHint)
    }

    @Test
    fun `toggling visibility writes to the repository`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)

        vm.onToggleVisible(beat.id, false)

        assertEquals(listOf("visible:1:false"), repo.calls)
    }

    @Test
    fun `add dialog opens and closes`() = runTest {
        val vm = PlatformsViewModel(FakeRepo())

        vm.onAddClick()
        assertEquals(PlatformDialog.Add(), vm.state.value.dialog)

        vm.onDismissDialog()
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `adding a valid name closes the dialog`() = runTest {
        val repo = FakeRepo()
        val vm = PlatformsViewModel(repo)
        vm.onAddClick()

        vm.onConfirmAdd("Cabify")

        assertEquals(listOf("create:Cabify"), repo.calls)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `invalid names keep the add dialog open with an inline error`() = runTest {
        val repo = FakeRepo()
        val vm = PlatformsViewModel(repo)
        vm.onAddClick()

        repo.next = PlatformResult.Blank
        vm.onConfirmAdd(" ")
        assertEquals(PlatformDialog.Add(PlatformNameError.Blank), vm.state.value.dialog)

        repo.next = PlatformResult.Duplicate
        vm.onConfirmAdd("beat")
        assertEquals(PlatformDialog.Add(PlatformNameError.Duplicate), vm.state.value.dialog)

        repo.next = PlatformResult.TooLong
        vm.onConfirmAdd("x".repeat(31))
        assertEquals(PlatformDialog.Add(PlatformNameError.TooLong), vm.state.value.dialog)
    }

    @Test
    fun `editing the name clears the inline error`() = runTest {
        val repo = FakeRepo()
        val vm = PlatformsViewModel(repo)
        vm.onAddClick()
        repo.next = PlatformResult.Duplicate
        vm.onConfirmAdd("beat")

        vm.onNameEdited()

        assertEquals(PlatformDialog.Add(), vm.state.value.dialog)
    }

    @Test
    fun `rename dialog carries the current name and confirms with the platform id`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)

        vm.onRenameClick(beat.id)
        assertEquals(PlatformDialog.Rename(id = 1, currentName = "Beat"), vm.state.value.dialog)

        vm.onConfirmRename("Cabify")
        assertEquals(listOf("rename:1:Cabify"), repo.calls)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `rename duplicate keeps the dialog open with an error`() = runTest {
        val repo = FakeRepo(listOf(beat, uber))
        val vm = PlatformsViewModel(repo)
        vm.onRenameClick(uber.id)
        repo.next = PlatformResult.Duplicate

        vm.onConfirmRename("beat")

        assertEquals(
            PlatformDialog.Rename(id = 2, currentName = "Uber", error = PlatformNameError.Duplicate),
            vm.state.value.dialog
        )
    }

    @Test
    fun `renaming a platform that vanished closes the dialog`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)
        vm.onRenameClick(beat.id)
        repo.next = PlatformResult.NotFound

        vm.onConfirmRename("Cabify")

        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `rename click for an unknown id opens nothing`() = runTest {
        val vm = PlatformsViewModel(FakeRepo(listOf(beat)))

        vm.onRenameClick(99L)

        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `delete asks for confirmation before touching the repository`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)

        vm.onDeleteClick(beat.id)
        assertEquals(PlatformDialog.Delete(id = 1, name = "Beat"), vm.state.value.dialog)
        assertTrue(repo.calls.isEmpty())

        vm.onConfirmDelete()
        assertEquals(listOf("delete:1"), repo.calls)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `cancelling the delete dialog deletes nothing`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)
        vm.onDeleteClick(beat.id)

        vm.onDismissDialog()

        assertTrue(repo.calls.isEmpty())
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `write failure posts a message and closes the dialog`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)
        repo.failure = IOException("disk full")
        vm.onAddClick()

        vm.onConfirmAdd("Cabify")

        assertEquals(R.string.platforms_save_error, vm.message.value?.textRes)
        assertNull(vm.state.value.dialog)
    }

    @Test
    fun `failed toggle posts distinct messages and shown clears only the matching id`() = runTest {
        val repo = FakeRepo(listOf(beat))
        val vm = PlatformsViewModel(repo)
        repo.failure = IOException("locked")

        vm.onToggleVisible(beat.id, false)
        val first = vm.message.value!!
        vm.onToggleVisible(beat.id, false)
        val second = vm.message.value!!

        assertNotEquals(first.id, second.id)
        vm.onMessageShown(first.id)
        assertEquals(second, vm.message.value)
        vm.onMessageShown(second.id)
        assertNull(vm.message.value)
    }
}
