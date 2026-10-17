package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState

/** Pure decisions behind [ShiftService.onStartCommand], kept out of the Android class to be testable. */
object ServiceStartPolicy {

    /**
     * Any delivered intent may come from `startForegroundService` / `getForegroundService`
     * (e.g. the notification "Iniciar carrera" action), so it must be answered with
     * `startForeground` before any early return. A null intent is a system restart: no obligation.
     */
    fun requiresForeground(hasIntent: Boolean): Boolean = hasIntent

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
}
