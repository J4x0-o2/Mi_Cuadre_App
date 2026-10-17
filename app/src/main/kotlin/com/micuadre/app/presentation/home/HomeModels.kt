package com.micuadre.app.presentation.home

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.finish.FinishSheetSubtitle
import com.micuadre.app.util.TimeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm")

/** Wall-clock `HH:mm` of an epoch-millis timestamp. */
fun clockTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    clockFormat.format(Instant.ofEpochMilli(epochMs).atZone(zone))

/** Day figures shown on Home. Only finished trips count; the open trip has no amount yet. */
data class DayBreakdown(
    val totalMinor: Long,
    val cashMinor: Long,
    val transferMinor: Long,
    val tripCount: Int
) {
    companion object {
        fun from(trips: List<Trip>): DayBreakdown {
            val finished = trips.filter { it.endedAt != null }
            fun sum(method: PaymentMethod) =
                finished.filter { it.paymentMethod == method }.sumOf { it.amountMinor ?: 0L }
            return DayBreakdown(
                totalMinor = finished.sumOf { it.amountMinor ?: 0L },
                cashMinor = sum(PaymentMethod.CASH),
                transferMinor = sum(PaymentMethod.TRANSFER),
                tripCount = finished.size
            )
        }
    }
}

/** One row of "Carreras de hoy". Platform names are localized by the UI from [platform]. */
data class TripRowModel(
    val id: Long,
    val platform: Platform,
    val otherLabel: String?,
    val badge: String,
    /** "HH:mm – HH:mm · N min": start, end and duration (at least one minute). */
    val detail: String,
    val amountMinor: Long?,
    val payment: PaymentMethod?
) {
    companion object {
        fun from(trip: Trip, zone: ZoneId = ZoneId.systemDefault()): TripRowModel {
            val end = trip.endedAt ?: trip.startedAt
            return TripRowModel(
                id = trip.id,
                platform = trip.platform,
                otherLabel = trip.platformOtherLabel?.trim()?.takeIf { it.isNotEmpty() },
                badge = PlatformBadge.text(trip.platform, trip.platformOtherLabel),
                detail = FinishSheetSubtitle.format(trip.startedAt, end, zone),
                amountMinor = trip.amountMinor,
                payment = trip.paymentMethod
            )
        }
    }
}

/** Short text inside the 42dp platform badge. */
object PlatformBadge {
    fun text(platform: Platform, otherLabel: String?): String = when (platform) {
        Platform.INDRIVER -> "iD"
        Platform.DIDI -> "D"
        Platform.OTHER -> otherLabel?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "O"
    }
}

/** Seconds since the shift started and since the current trip started (0 when not on a trip). */
data class Elapsed(val shiftSec: Long, val tripSec: Long) {
    companion object {
        fun of(state: ShiftState, nowMs: Long): Elapsed = when (state) {
            is ShiftState.Disconnected -> Elapsed(0L, 0L)
            is ShiftState.Connected -> Elapsed(TimeUtils.elapsedSeconds(state.connectedSinceMs, nowMs), 0L)
            is ShiftState.OnTrip -> Elapsed(
                TimeUtils.elapsedSeconds(state.connectedSinceMs, nowMs),
                TimeUtils.elapsedSeconds(state.tripStartedAtMs, nowMs)
            )
        }
    }
}

/**
 * Gold arc of the on-trip ring. Decorative and deliberately slow: it advances once per
 * elapsed minute over a [REFERENCE_MINUTES] reference trip, never fully empty (so the
 * gold is visible from the first second) and capped at a full circle.
 */
object RingProgress {
    const val MIN_FRACTION = 0.05f
    const val REFERENCE_MINUTES = 30

    fun fraction(tripSec: Long): Float {
        val minutes = (tripSec.coerceAtLeast(0L) / 60L).toFloat()
        return (minutes / REFERENCE_MINUTES).coerceIn(MIN_FRACTION, 1f)
    }
}

/** Secondary (outlined) actions shown at the bottom of Home. */
enum class HomeAction { DISCONNECT, END_SHIFT }

object HomeActions {
    /** Business rule: a trip must be finished before disconnecting. */
    fun canDisconnect(state: ShiftState): Boolean = state !is ShiftState.OnTrip

    /**
     * Secondary actions available on Home, in display order. Connected can pause ("Desconectar")
     * or end the shift; a paused shift (Disconnected with an open session) can still be ended.
     * On a trip the on-trip view owns the (disabled) disconnect, so nothing is offered here.
     */
    fun secondary(state: ShiftState, hasOpenShift: Boolean): List<HomeAction> = when (state) {
        is ShiftState.Connected -> listOf(HomeAction.DISCONNECT, HomeAction.END_SHIFT)
        is ShiftState.Disconnected -> if (hasOpenShift) listOf(HomeAction.END_SHIFT) else emptyList()
        is ShiftState.OnTrip -> emptyList()
    }
}
