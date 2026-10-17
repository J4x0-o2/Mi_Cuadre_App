package com.micuadre.app.presentation.summary

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.home.DayBreakdown
import com.micuadre.app.presentation.home.clockTime
import com.micuadre.app.util.Formatters
import java.time.ZoneId

/** One "Por plataforma" bar. [fraction] is relative to the platform with the highest total. */
data class PlatformBar(
    val platform: Platform,
    val otherLabel: String?,
    val totalMinor: Long,
    val tripCount: Int,
    val fraction: Float
)

/** Everything the shift summary screen shows, already derived and formatted. */
data class ShiftSummaryModel(
    val startTime: String,
    val endTime: String,
    val totalMinor: Long,
    val connectedText: String,
    val tripCount: Int,
    val cashMinor: Long,
    val transferMinor: Long,
    val bars: List<PlatformBar>
) {
    companion object {
        fun from(summary: DailySummary, zone: ZoneId = ZoneId.systemDefault()): ShiftSummaryModel {
            val finished = summary.trips.filter { it.endedAt != null }
            val day = DayBreakdown.from(finished)
            val end = summary.endedAt ?: summary.startedAt
            val connectedSec = ((end - summary.startedAt) / 1000L).coerceAtLeast(0L)
            return ShiftSummaryModel(
                startTime = clockTime(summary.startedAt, zone),
                endTime = clockTime(end, zone),
                totalMinor = day.totalMinor,
                connectedText = Formatters.durationHMSS(connectedSec),
                tripCount = day.tripCount,
                cashMinor = day.cashMinor,
                transferMinor = day.transferMinor,
                bars = platformBars(finished)
            )
        }

        private fun platformBars(finished: List<Trip>): List<PlatformBar> {
            // "Otra" trips are grouped by their (case-insensitive) label, other platforms by enum.
            val groups = finished.groupBy { trip ->
                if (trip.platform == Platform.OTHER) {
                    trip.platform to trip.platformOtherLabel?.trim()?.lowercase().orEmpty()
                } else {
                    trip.platform to ""
                }
            }
            val totals = groups.map { (_, trips) ->
                val label = trips.firstNotNullOfOrNull {
                    it.platformOtherLabel?.trim()?.takeIf { l -> l.isNotEmpty() }
                }
                Triple(trips.first().platform, label, trips)
            }.map { (platform, label, trips) ->
                PlatformBar(
                    platform = platform,
                    otherLabel = if (platform == Platform.OTHER) label else null,
                    totalMinor = trips.sumOf { it.amountMinor ?: 0L },
                    tripCount = trips.size,
                    fraction = 0f
                )
            }.sortedByDescending { it.totalMinor }
            val max = totals.maxOfOrNull { it.totalMinor } ?: 0L
            return totals.map {
                it.copy(fraction = if (max > 0L) it.totalMinor.toFloat() / max else 0f)
            }
        }
    }
}
