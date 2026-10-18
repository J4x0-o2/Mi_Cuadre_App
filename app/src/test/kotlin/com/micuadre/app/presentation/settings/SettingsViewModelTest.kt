package com.micuadre.app.presentation.settings

import com.micuadre.app.R
import com.micuadre.app.domain.model.ThemeMode
import com.micuadre.app.domain.repository.ThemePreferenceRepository
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun exposesTheStoredMode() = runTest {
        val vm = SettingsViewModel(FakeRepo(ThemeMode.DARK))
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
    }

    @Test
    fun selectingPersistsAndUpdatesTheExposedMode() = runTest {
        val repo = FakeRepo()
        val vm = SettingsViewModel(repo)
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
        val vm = SettingsViewModel(FailingWriteRepo())
        vm.onSelect(ThemeMode.DARK)
        assertEquals(ThemeMode.LIGHT, vm.themeMode.value)
        assertEquals(R.string.settings_save_error, vm.message.value?.textRes)
    }

    @Test
    fun repeatedFailuresPostDistinctMessagesAndShownClearsIt() = runTest {
        val vm = SettingsViewModel(FailingWriteRepo())
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
        val vm = SettingsViewModel(FakeRepo())
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
}
