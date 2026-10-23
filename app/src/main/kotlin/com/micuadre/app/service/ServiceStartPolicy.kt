package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState

/** Pure decisions behind [ShiftService.onStartCommand], kept out of the Android class to be testable. */
object ServiceStartPolicy {

    /**
     * An idle service (nothing restored, nothing connecting) must not linger holding a
     * foreground notification. Waits for rehydration, any in-flight connect and the first
     * start command, so a normal Connect is never cut short.
     */
    fun shouldStopWhenIdle(
        state: ShiftState,
        rehydrated: Boolean,
        connectPending: Boolean,
        startCommandSeen: Boolean
    ): Boolean = state is ShiftState.Disconnected && rehydrated && !connectPending && startCommandSeen

    /**
     * Opening the app with a restorable shift (connected or on a ride, not paused) but nothing published by a running service (the
     * process was killed and the system restart was refused or never came): ask the service to
     * restore it. With no shift stored nothing is started, so a cold launch shows no notification.
     */
    fun shouldRestoreOnAppOpen(state: ShiftState, hasRestorableShift: Boolean): Boolean =
        state is ShiftState.Disconnected && hasRestorableShift
}
