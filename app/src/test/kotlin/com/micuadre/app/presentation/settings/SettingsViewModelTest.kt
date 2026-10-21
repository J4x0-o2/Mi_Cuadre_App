package com.micuadre.app.presentation.settings

import com.micuadre.app.R
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformCounts
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.model.ThemeMode
import com.micuadre.app.domain.repository.BubblePreferenceRepository
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.domain.repository.PlatformRepository
import com.micuadre.app.domain.repository.PlatformResult
import com.micuadre.app.domain.repository.ThemePreferenceRepository
import com.micuadre.app.overlay.OverlayPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.IOException
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class FakeRepo(initial: ThemeMode = ThemeMode.SYSTEM) : ThemePreferenceRepository {
        val state = MutableStateFlow(initial)
        override val themeMode: Flow<ThemeMode> = state
        override suspend fun setThemeMode(mode: ThemeMode) {
            state.value = mode
        }
    }

    private class FakeBubbleRepo(initial: Boolean = false) : BubblePreferenceRepository {
        val state = MutableStateFlow(initial)
        override val bubbleEnabled: Flow<Boolean> = state
        override suspend fun setBubbleEnabled(enabled: Boolean) {
            state.value = enabled
        }
    }

    private class FakePermission(var granted: Boolean = false) : OverlayPermission {
        override fun canDrawOverlays() = granted
    }

    private fun viewModel(
        theme: ThemePreferenceRepository = FakeRepo(),
        platforms: PlatformRepository = NoPlatforms,
        bubble: BubblePreferenceRepository = FakeBubbleRepo(),
        permission: OverlayPermission = FakePermission(),
        deductions: DeductionRepository = NoDeductions
    ) = SettingsViewModel(theme, platforms, deductions, bubble, permission)

    private object NoDeductions : DeductionRepository {
        override fun observeActive(): Flow<List<Deduction>> = flowOf(emptyList())
        override fun observeVisible(): Flow<List<Deduction>> = flowOf(emptyList())
        override suspend fun create(name: String, usualAmountMinor: Long?) = DeductionResult.Ok
        override suspend fun rename(id: Long, name: String) = DeductionResult.Ok
        override suspend fun setVisible(id: Long, visible: Boolean) = DeductionResult.Ok
        override suspend fun setUsualAmount(id: Long, amountMinor: Long?) = DeductionResult.Ok
        override suspend fun update(id: Long, name: String, usualAmountMinor: Long?) = DeductionResult.Ok
        override suspend fun delete(id: Long) = DeductionResult.Ok
        override suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>) = DeductionResult.Ok
        override suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount> = emptyList()
        override fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>> =
            flowOf(emptyList())
    }

    private object NoPlatforms : PlatformRepository {
        override fun observeActive(): Flow<List<Platform>> = flowOf(emptyList())
        override suspend fun create(name: String) = PlatformResult.Ok
        override suspend fun rename(id: Long, name: String) = PlatformResult.Ok
        override suspend fun setVisible(id: Long, visible: Boolean) = PlatformResult.Ok
        override suspend fun delete(id: Long) = PlatformResult.Ok
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposesTheStoredMode() = runTest {
        val vm = viewModel(theme = FakeRepo(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun selectingPersistsAndUpdatesTheExposedMode() = runTest {
        val repo = FakeRepo()
        val vm = viewModel(theme = repo)
        vm.onSelect(ThemeMode.SCHEDULED)
        assertEquals(ThemeMode.SCHEDULED, repo.state.value)
        assertEquals(ThemeMode.SCHEDULED, vm.themeMode.value)
    }

    private class FailingWriteRepo : ThemePreferenceRepository {
        override val themeMode: Flow<ThemeMode> = MutableStateFlow(ThemeMode.LIGHT)
        override suspend fun setThemeMode(mode: ThemeMode) = throw IOException("disk full")
    }

    @Test
    fun failedWriteKeepsPreviousModeAndPostsAMessage() = runTest {
        val vm = viewModel(theme = FailingWriteRepo())
        vm.onSelect(ThemeMode.DARK)
        assertEquals(ThemeMode.LIGHT, vm.themeMode.value)
        assertEquals(R.string.settings_save_error, vm.message.value?.textRes)
    }

    @Test
    fun repeatedFailuresPostDistinctMessagesAndShownClearsIt() = runTest {
        val vm = viewModel(theme = FailingWriteRepo())
        vm.onSelect(ThemeMode.DARK)
        val first = vm.message.value!!
        vm.onSelect(ThemeMode.DARK)
        val second = vm.message.value!!
        assertNotEquals(first.id, second.id)
        vm.onMessageShown(first.id) // stale id must not clear the newer message
        assertEquals(second, vm.message.value)
        vm.onMessageShown(second.id)
        assertNull(vm.message.value)
    }

    @Test
    fun successfulWritePostsNoMessage() = runTest {
        val vm = viewModel()
        vm.onSelect(ThemeMode.DARK)
        assertNull(vm.message.value)
    }

    @Test
    fun optionsListEveryModeOnceInDisplayOrder() {
        assertEquals(
            listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SCHEDULED),
            ThemeOptions.all.map { it.mode }
        )
        assertEquals(R.string.theme_system, ThemeOptions.all.first().labelRes)
        assertEquals(R.string.theme_scheduled_desc, ThemeOptions.all.last().descriptionRes)
    }

    @Test
    fun platformCountsSummariseVisibleAgainstTotal() = runTest {
        val platforms = object : PlatformRepository by NoPlatforms {
            override fun observeActive(): Flow<List<Platform>> =
                flowOf(listOf(Platform(1, "A"), Platform(2, "B", visible = false), Platform(3, "C")))
        }
        val vm = viewModel(platforms = platforms)
        assertEquals(PlatformCounts(visible = 2, total = 3), vm.platformCounts.value)
    }

    @Test
    fun deductionNamesListActiveDeductionsInOrderHiddenOnesIncluded() = runTest {
        val deductions = object : DeductionRepository by NoDeductions {
            override fun observeActive(): Flow<List<Deduction>> =
                flowOf(listOf(Deduction(1, "Combustible"), Deduction(2, "Comida", visible = false)))
        }
        val vm = viewModel(deductions = deductions)
        assertEquals(listOf("Combustible", "Comida"), vm.deductionNames.value)
    }

    @Test
    fun deductionNamesAreEmptyWhenThereAreNoDeductions() = runTest {
        assertEquals(emptyList<String>(), viewModel().deductionNames.value)
    }

    @Test
    fun bubbleStartsOffAndWithoutAPendingPermission() = runTest {
        val vm = viewModel(permission = FakePermission(granted = false))
        assertEquals(BubbleSettings(enabled = false, permissionGranted = false), vm.bubble.value)
        assertEquals(false, vm.bubble.value.permissionPending)
    }

    @Test
    fun turningTheBubbleOnWithoutPermissionSavesItAndAsksForThePermission() = runTest {
        val repo = FakeBubbleRepo()
        val vm = viewModel(bubble = repo, permission = FakePermission(granted = false))
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.permissionRequests.collect { requests++ } }

        vm.onBubbleToggled(true)

        assertTrue(repo.state.value)
        assertEquals(1, requests)
        assertTrue(vm.bubble.value.permissionPending)
    }

    @Test
    fun turningTheBubbleOnWithPermissionDoesNotAskAgain() = runTest {
        val vm = viewModel(permission = FakePermission(granted = true))
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.permissionRequests.collect { requests++ } }

        vm.onBubbleToggled(true)

        assertEquals(0, requests)
        assertEquals(BubbleSettings(enabled = true, permissionGranted = true), vm.bubble.value)
    }

    @Test
    fun turningTheBubbleOffNeverAsksForThePermission() = runTest {
        val vm = viewModel(bubble = FakeBubbleRepo(initial = true), permission = FakePermission(granted = false))
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.permissionRequests.collect { requests++ } }

        vm.onBubbleToggled(false)

        assertEquals(0, requests)
        assertEquals(false, vm.bubble.value.enabled)
    }

    @Test
    fun resumingAfterGrantingThePermissionClearsThePendingState() = runTest {
        val permission = FakePermission(granted = false)
        val vm = viewModel(bubble = FakeBubbleRepo(initial = true), permission = permission)
        assertTrue(vm.bubble.value.permissionPending)

        permission.granted = true
        vm.onResume()

        assertEquals(false, vm.bubble.value.permissionPending)
    }

    @Test
    fun retryingThePermissionAsksForItAgainWithoutChangingTheToggle() = runTest {
        val repo = FakeBubbleRepo(initial = true)
        val vm = viewModel(bubble = repo, permission = FakePermission(granted = false))
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.permissionRequests.collect { requests++ } }

        vm.onBubblePermissionRetry()

        assertEquals(1, requests)
        assertTrue(repo.state.value)
    }

    private class FailingBubbleRepo : BubblePreferenceRepository {
        override val bubbleEnabled: Flow<Boolean> = MutableStateFlow(false)
        override suspend fun setBubbleEnabled(enabled: Boolean) = throw IOException("disk full")
    }

    @Test
    fun failedBubbleWriteKeepsItOffPostsAMessageAndSkipsThePermissionRequest() = runTest {
        val vm = viewModel(bubble = FailingBubbleRepo())
        var requests = 0
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.permissionRequests.collect { requests++ } }

        vm.onBubbleToggled(true)

        assertEquals(false, vm.bubble.value.enabled)
        assertEquals(0, requests)
        assertEquals(R.string.settings_save_error, vm.message.value?.textRes)
    }

    @Test
    fun missingPermissionSettingsPageExplainsHowToGrantItManually() = runTest {
        val vm = viewModel()

        vm.onPermissionSettingsUnavailable()

        assertEquals(R.string.settings_bubble_permission_unavailable, vm.message.value?.textRes)
    }
}
