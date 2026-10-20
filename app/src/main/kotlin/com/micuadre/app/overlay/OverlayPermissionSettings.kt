package com.micuadre.app.overlay

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Opens the system page where the user grants "display over other apps".
 * Some OEM and low-RAM builds lack the overlay page, so it falls back to the generic overlay
 * list and then to the app details page instead of crashing.
 */
object OverlayPermissionSettings {

    /** Pages to try, most specific first. */
    fun candidates(packageName: String): List<Intent> {
        val packageUri = Uri.parse("package:$packageName")
        return listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
        ).map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) } // works from any context
    }

    /** Starts the first page that exists; false when none does. */
    fun open(context: Context): Boolean = candidates(context.packageName).any { intent ->
        try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }
}
