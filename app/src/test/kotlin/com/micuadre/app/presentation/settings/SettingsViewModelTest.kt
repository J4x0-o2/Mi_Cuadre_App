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
