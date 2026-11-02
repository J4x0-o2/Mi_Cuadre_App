package com.micuadre.app.presentation.settings

import com.micuadre.app.R
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import com.micuadre.app.flowta.FlowTaAvailability
import com.micuadre.app.flowta.FlowTaClient
import com.micuadre.app.flowta.FlowTaLinkStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class FlowTaSyncViewModelTest {

    private class FakePrefs(initial: Boolean = false) : FlowTaSyncPreferenceRepository {
        val state = MutableStateFlow(initial)
        var failWrites = false
        override fun observe(): Flow<Boolean> = state
        override suspend fun setEnabled(enabled: Boolean) {
            if (failWrites) throw IOException("disk full")
            state.value = enabled
        }
    }

    private class FakeAvailability(var installed: Boolean = true) : FlowTaAvailability {
        override fun isInstalled() = installed
    }

    private class FakeClient(var status: FlowTaLinkStatus = FlowTaLinkStatus.LINKED) : FlowTaClient {
        var calls = 0
        override suspend fun linkStatus(): FlowTaLinkStatus {
            calls++
            return status
        }
    }

    private val prefs = FakePrefs()
    private val availability = FakeAvailability()
    private val client = FakeClient()

    private fun viewModel() = FlowTaSyncViewModel(prefs, availability, client)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.collectEffects(vm: FlowTaSyncViewModel): List<FlowTaSyncEffect> {
        val effects = mutableListOf<FlowTaSyncEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effects.collect { effects += it } }
        return effects
    }

    @Test
    fun `turning on without FlowTa installed shows the install dialog and stays off`() = runTest {
        availability.installed = false
        val vm = viewModel()
        val effects = collectEffects(vm)
        vm.onToggled(true)
        assertTrue(vm.state.value.showInstallDialog)
        assertFalse(vm.state.value.enabled)
        assertTrue(effects.isEmpty())
        assertFalse(prefs.state.value)
    }

    @Test
    fun `dismissing the install dialog hides it`() = runTest {
        availability.installed = false
        val vm = viewModel()
        vm.onToggled(true)
        vm.onInstallDialogDismissed()
        assertFalse(vm.state.value.showInstallDialog)
    }

    @Test
    fun `turning on with FlowTa installed launches connect and does not persist yet`() = runTest {
        val vm = viewModel()
        val effects = collectEffects(vm)
        vm.onToggled(true)
        assertEquals(listOf(FlowTaSyncEffect.LaunchConnect), effects)
        assertFalse(prefs.state.value)
        assertFalse(vm.state.value.showInstallDialog)
    }

    @Test
    fun `connect result OK persists enabled`() = runTest {
        val vm = viewModel()
        vm.onToggled(true)
        vm.onConnectResult(true)
        assertTrue(prefs.state.value)
        assertTrue(vm.state.value.enabled)
    }

    @Test
    fun `connect result canceled leaves it off`() = runTest {
        val vm = viewModel()
        vm.onToggled(true)
        vm.onConnectResult(false)
        assertFalse(prefs.state.value)
        assertFalse(vm.state.value.enabled)
    }

    @Test
    fun `turning off persists false without asking FlowTa`() = runTest {
        prefs.state.value = true
        val vm = viewModel()
        val callsBefore = client.calls
        vm.onToggled(false)
        assertFalse(prefs.state.value)
        assertFalse(vm.state.value.enabled)
        assertEquals(callsBefore, client.calls)
    }

    @Test
    fun `a failed launch posts a message and stays off`() = runTest {
        val vm = viewModel()
        vm.onLaunchFailed()
        assertEquals(R.string.settings_flowta_launch_error, vm.message.value?.textRes)
        assertFalse(prefs.state.value)
    }

    @Test
    fun `a failed write posts the save error and keeps the stored value`() = runTest {
        val vm = viewModel()
        prefs.failWrites = true
        vm.onConnectResult(true)
        assertEquals(R.string.settings_save_error, vm.message.value?.textRes)
        assertFalse(vm.state.value.enabled)
    }

    @Test
    fun `enabled and NOT_LINKED shows reconnect`() = runTest {
        prefs.state.value = true
        client.status = FlowTaLinkStatus.NOT_LINKED
        val vm = viewModel()
        vm.onResume()
        assertTrue(vm.state.value.showReconnect)
    }

    @Test
    fun `LINKED and UNAVAILABLE hide reconnect`() = runTest {
        prefs.state.value = true
        val vm = viewModel()
        client.status = FlowTaLinkStatus.LINKED
        vm.onResume()
        assertFalse(vm.state.value.showReconnect)
        client.status = FlowTaLinkStatus.UNAVAILABLE
        vm.onResume()
        assertFalse(vm.state.value.showReconnect)
    }

    @Test
    fun `reconnect is hidden and link status not queried while disabled`() = runTest {
        client.status = FlowTaLinkStatus.NOT_LINKED
        val vm = viewModel()
        vm.onResume()
        assertFalse(vm.state.value.showReconnect)
        assertEquals(0, client.calls)
    }

    @Test
    fun `reconnect launches connect and OK hides the hint while staying enabled`() = runTest {
        prefs.state.value = true
        client.status = FlowTaLinkStatus.NOT_LINKED
        val vm = viewModel()
        val effects = collectEffects(vm)
        vm.onResume()
        vm.onReconnectClicked()
        assertEquals(listOf(FlowTaSyncEffect.LaunchConnect), effects)
        vm.onConnectResult(true)
        assertTrue(vm.state.value.enabled)
        assertFalse(vm.state.value.showReconnect)
    }

    @Test
    fun `reconnect canceled keeps the hint`() = runTest {
        prefs.state.value = true
        client.status = FlowTaLinkStatus.NOT_LINKED
        val vm = viewModel()
        vm.onResume()
        vm.onConnectResult(false)
        assertTrue(vm.state.value.enabled)
        assertTrue(vm.state.value.showReconnect)
    }

    @Test
    fun `turning off hides the reconnect hint`() = runTest {
        prefs.state.value = true
        client.status = FlowTaLinkStatus.NOT_LINKED
        val vm = viewModel()
        vm.onResume()
        vm.onToggled(false)
        assertFalse(vm.state.value.showReconnect)
    }
}
