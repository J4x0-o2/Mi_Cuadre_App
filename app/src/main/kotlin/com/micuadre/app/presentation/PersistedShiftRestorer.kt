package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.service.ServiceStartPolicy
import kotlinx.coroutines.CancellationException

/**
 * Brings back a shift left open in Room when the app opens, even if the service was never restarted
 * (or its restart was refused). Everything the Android world provides is injected, so it is
 * JVM-testable like [ConnectCoordinator].
 */
class PersistedShiftRestorer(
    private val state: () -> ShiftState,
    private val hasActiveSession: suspend () -> Boolean,
    private val restore: () -> Unit,
    private val onFailure: (Throwable) -> Unit
) {

    suspend fun restoreIfNeeded() {
        val hasSession = try {
            hasActiveSession()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Exception) {
            // Without knowing whether a shift is stored, starting a service would be a guess.
            onFailure(t)
            return
        }
        if (ServiceStartPolicy.shouldRestoreOnAppOpen(state(), hasSession)) restore()
    }
}
