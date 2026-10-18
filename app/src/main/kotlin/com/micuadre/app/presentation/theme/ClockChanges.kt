package com.micuadre.app.presentation.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Emits whenever the system time zone or wall clock changes. The receiver is registered only
 * while the flow is collected, so it lives exactly as long as the theme state is observed.
 */
fun clockChanges(context: Context): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            trySend(Unit)
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_TIMEZONE_CHANGED)
        addAction(Intent.ACTION_TIME_CHANGED)
    }
    context.registerReceiver(receiver, filter)
    awaitClose { context.unregisterReceiver(receiver) }
}
