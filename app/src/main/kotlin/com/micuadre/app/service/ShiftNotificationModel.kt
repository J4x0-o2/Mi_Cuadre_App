package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Buttons the ongoing notification can show. */
enum class NotificationAction { FINISH_TRIP, START_TRIP, OPEN_APP }

/** Day total and trip count shown in the notification. */
data class DayTotals(val totalMinor: Long, val tripCount: Int) {
    companion object {
        val EMPTY = DayTotals(0L, 0)
        fun from(trips: List<Trip>): DayTotals {
            val finished = trips.filter { it.endedAt != null }
            return DayTotals(finished.sumOf { it.amountMinor ?: 0L }, finished.size)
        }
    }
}

/** Copy provider so the model stays free of Android resources. */
interface NotificationTexts {
    fun idleTitle(): String
    fun tripTitle(): String
    fun tripText(startedAtHHmm: String, total: String, tripCount: Int): String
    fun connectedTitle(total: String, tripCount: Int): String
    fun connectedText(): String
}

/** Pure description of the notification for one shift state. */
data class ShiftNotificationModel(
    val title: String,
    val text: String,
    /** Epoch ms the system chronometer counts from; null when no chronometer is shown. */
    val chronometerBaseMs: Long?,
    val actions: List<NotificationAction>
) {
    companion object {
        fun create(
            state: ShiftState,
            totals: DayTotals,
            texts: NotificationTexts,
            formatMoney: (Long) -> String,
            zone: ZoneId = ZoneId.systemDefault()
        ): ShiftNotificationModel = when (state) {
            is ShiftState.Disconnected -> ShiftNotificationModel(
                title = texts.idleTitle(),
                text = "",
                chronometerBaseMs = null,
                actions = listOf(NotificationAction.OPEN_APP)
            )
            is ShiftState.Connected -> ShiftNotificationModel(
                title = texts.connectedTitle(formatMoney(totals.totalMinor), totals.tripCount),
                text = texts.connectedText(),
                chronometerBaseMs = null,
                actions = listOf(NotificationAction.START_TRIP, NotificationAction.OPEN_APP)
            )
            is ShiftState.OnTrip -> ShiftNotificationModel(
                title = texts.tripTitle(),
                text = texts.tripText(
                    startedAtHHmm = timeFormat.format(Instant.ofEpochMilli(state.tripStartedAtMs).atZone(zone)),
                    total = formatMoney(totals.totalMinor),
                    tripCount = totals.tripCount
                ),
                chronometerBaseMs = state.tripStartedAtMs,
                actions = listOf(NotificationAction.FINISH_TRIP, NotificationAction.OPEN_APP)
            )
        }

        private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    }
}
