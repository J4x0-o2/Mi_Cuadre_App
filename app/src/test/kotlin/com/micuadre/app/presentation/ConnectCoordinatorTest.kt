package com.micuadre.app.presentation

import com.micuadre.app.presentation.ConnectCoordinator.Effect
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectCoordinatorTest {

    private var starts = 0
    private val coordinator = ConnectCoordinator { starts++ }

    @Test
    fun `permission not required starts the shift immediately`() {
        assertEquals(Effect.None, coordinator.onConnectTapped(NotificationPermission.NotRequired))
        assertEquals(1, starts)
    }

    @Test
    fun `permission already granted starts the shift immediately`() {
        assertEquals(Effect.None, coordinator.onConnectTapped(NotificationPermission.Granted))
        assertEquals(1, starts)
    }

    @Test
    fun `permission needed requests it and does not start the shift yet`() {
        assertEquals(Effect.RequestPermission, coordinator.onConnectTapped(NotificationPermission.NeedsRequest))
        assertEquals(0, starts)
    }

    @Test
    fun `granted result starts the shift once without a hint`() {
        coordinator.onConnectTapped(NotificationPermission.NeedsRequest)
        assertEquals(Effect.None, coordinator.onPermissionResult(granted = true))
        assertEquals(1, starts)
    }

    @Test
    fun `denied result still starts the shift and shows the hint`() {
        coordinator.onConnectTapped(NotificationPermission.NeedsRequest)
        assertEquals(Effect.ShowDeniedHint, coordinator.onPermissionResult(granted = false))
        assertEquals(1, starts)
    }

    @Test
    fun `a second tap while the prompt is pending does not request again`() {
        coordinator.onConnectTapped(NotificationPermission.NeedsRequest)
        assertEquals(Effect.None, coordinator.onConnectTapped(NotificationPermission.NeedsRequest))
        coordinator.onPermissionResult(granted = true)
        assertEquals(1, starts)
    }

    @Test
    fun `a result without a pending request does not start the shift`() {
        assertEquals(Effect.None, coordinator.onPermissionResult(granted = true))
        assertEquals(0, starts)
    }

    @Test
    fun `the pending request is consumed by its result`() {
        coordinator.onConnectTapped(NotificationPermission.NeedsRequest)
        coordinator.onPermissionResult(granted = true)
        coordinator.onPermissionResult(granted = true)
        assertEquals(1, starts)
    }
}
