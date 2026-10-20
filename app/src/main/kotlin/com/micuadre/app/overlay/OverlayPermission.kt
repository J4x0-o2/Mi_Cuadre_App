package com.micuadre.app.overlay

import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Whether the user granted "display over other apps" (SYSTEM_ALERT_WINDOW) to this app. */
interface OverlayPermission {
    fun canDrawOverlays(): Boolean
}

class SystemOverlayPermission @Inject constructor(
    @ApplicationContext private val context: Context
) : OverlayPermission {
    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)
}
