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
import com.micuadre.app.presentation.LaunchRouting
import com.micuadre.app.util.Formatters

/**
 * Builds the persistent service notification from a [ShiftNotificationModel].
 *
 * Channel:
 *  - id: `micuadre_shift`
 *  - importance: LOW (no sound on update)
 *
 * Live trip time comes from the system chronometer, so the notification only
 * needs to be re-posted when the state or the day totals change.
 */
class NotificationFactory(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "micuadre_shift"
        const val NOTIF_ID = 1001

        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_FINISH_TRIP = 1
        private const val REQUEST_START_TRIP = 2
        private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }

    private val texts = object : NotificationTexts {
        override fun idleTitle() = context.getString(R.string.app_name)
        override fun tripTitle() = context.getString(R.string.notif_trip_title)
        override fun tripText(startedAtHHmm: String, total: String, tripCount: Int) =
            context.getString(R.string.notif_trip_text, startedAtHHmm, total, tripCountLabel(tripCount))
        override fun connectedTitle(total: String, tripCount: Int) =
            context.getString(R.string.notif_connected_title, total, tripCountLabel(tripCount))
        override fun connectedText() = context.getString(R.string.notif_connected_text)
    }

    private fun tripCountLabel(count: Int): String =
        context.resources.getQuantityString(R.plurals.trip_count_label, count, count)

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

    fun build(state: ShiftState, totals: DayTotals): Notification {
        val model = ShiftNotificationModel.create(
            state = state,
            totals = totals,
            texts = texts,
            formatMoney = Formatters::moneyNoDecimals
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_micuadre)
            .setContentTitle(model.title)
            .setContentText(model.text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent())

        val base = model.chronometerBaseMs
        if (base != null) {
            builder.setShowWhen(true).setWhen(base).setUsesChronometer(true)
        } else {
            builder.setShowWhen(false)
        }

        model.actions.forEach { action ->
            when (action) {
                NotificationAction.FINISH_TRIP -> builder.addAction(
                    0, context.getString(R.string.notif_action_finish_trip), finishTripIntent()
                )
                NotificationAction.START_TRIP -> builder.addAction(
                    0, context.getString(R.string.notif_action_start_trip), startTripIntent()
                )
                NotificationAction.OPEN_APP -> builder.addAction(
                    0, context.getString(R.string.notif_action_open_app), openAppIntent()
                )
            }
        }
        return builder.build()
    }

    private fun mainActivityIntent(action: String? = null) =
        Intent(context, MainActivity::class.java).apply {
            this.action = action
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(context, REQUEST_OPEN_APP, mainActivityIntent(), FLAGS)

    /** Opens the app straight into the finish sheet. */
    private fun finishTripIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_FINISH_TRIP,
        mainActivityIntent(LaunchRouting.ACTION_OPEN_FINISH_TRIP),
        FLAGS
    )

    /** Starts the trip through the already running service; the app is not opened. */
    private fun startTripIntent(): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_START_TRIP,
        Intent(context, ShiftService::class.java).setAction(ShiftServiceController.ACTION_START_TRIP),
        FLAGS
    )
}
