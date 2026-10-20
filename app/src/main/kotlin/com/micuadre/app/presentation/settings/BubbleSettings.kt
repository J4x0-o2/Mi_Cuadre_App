package com.micuadre.app.presentation.settings

/** What the "Burbuja flotante" row shows: the stored toggle plus the live overlay permission. */
data class BubbleSettings(
    val enabled: Boolean,
    val permissionGranted: Boolean
) {
    /** Turned on, but the system permission is still missing, so the bubble cannot appear. */
    val permissionPending: Boolean get() = enabled && !permissionGranted
}
