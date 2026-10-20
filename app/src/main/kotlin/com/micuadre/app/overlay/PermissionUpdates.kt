package com.micuadre.app.overlay

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits the current permission state, then keeps re-checking every [pollMs] while it is missing
 * and emits `true` once it is granted (the user grants it in a system page, so there is no
 * callback). Polling stops by itself afterwards or when the collector goes away.
 */
fun permissionUpdates(canDraw: () -> Boolean, pollMs: Long): Flow<Boolean> = flow {
    var granted = canDraw()
    emit(granted)
    while (!granted) {
        delay(pollMs)
        granted = canDraw()
        if (granted) emit(true)
    }
}
