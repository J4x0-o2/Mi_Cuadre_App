package com.micuadre.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.micuadre.app.MainActivity
import com.micuadre.app.R
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.util.Formatters

/**
 * Builds and publishes the persistent service notifications.
 *
 * Channel:
 *  - id: `micuadre_shift`
 *  - importance: LOW (no sound on update; ticker only on first post)
 */
class NotificationFactory(
    private val context: Context,
    private val nowMs: () -> Long
) {

    companion object {
        const val CHANNEL_ID = "micuadre_shift"
        const val NOTIF_ID = 1001
    }

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            setShowBadge(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    fun build(state: ShiftState): Notification {
        val (title, contentTitle) = when (state) {
            is ShiftState.Disconnected -> context.getString(R.string.app_name) to "—"
            is ShiftState.Connected -> {
                val elapsedSec = (nowMs() - state.connectedSinceMs).coerceAtLeast(0L) / 1000L
                context.getString(R.string.notif_connected, Formatters.durationHMMSS(elapsedSec)) to
                    context.getString(R.string.notif_connected, Formatters.durationHMMSS(elapsedSec))
            }
            is ShiftState.OnTrip -> {
                val shiftSec = (nowMs() - state.connectedSinceMs).coerceAtLeast(0L) / 1000L
                val tripSec = (nowMs() - state.tripStartedAtMs).coerceAtLeast(0L) / 1000L
                val title = context.getString(R.string.notif_on_trip, Formatters.durationMMSS(tripSec))
                val content = context.getString(R.string.notif_connected, Formatters.durationHMMSS(shiftSec))
                title to content
            }
        }

        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_micuadre)
            .setContentTitle(title)
            .setContentText(contentTitle)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openApp)
            .setShowWhen(false)
            .build()
    }
}
