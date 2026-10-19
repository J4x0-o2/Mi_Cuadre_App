package com.micuadre.app.presentation.summary

import com.micuadre.app.domain.model.ConnectedTime
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.presentation.home.DayBreakdown
import com.micuadre.app.util.Formatters
import java.time.ZoneId

/** One "Por plataforma" bar. [fraction] is relative to the platform with the highest total. */
data class PlatformBar(
    val platformId: Long,
    val name: String,
    val totalMinor: Long,
    val tripCount: Int,
    val fraction: Float
)

/** Everything the shift summary screen shows, already derived and formatted. */
data class ShiftSummaryModel(
    val range: SummaryRange,
    val totalMinor: Long,
    val connectedText: String,
    val tripCount: Int,
    val cashMinor: Long,
    val transferMinor: Long,
    val bars: List<PlatformBar>
) {
    companion object {
        fun from(
            summary: DailySummary,
            zone: ZoneId = ZoneId.systemDefault(),
            nowMs: Long = System.currentTimeMillis()
        ): ShiftSummaryModel {
            val finished = summary.trips.filter { it.endedAt != null }
            val day = DayBreakdown.from(finished)
            val connectedSec = ConnectedTime.totalSeconds(
                summary.connectedIntervals,
                nowMs = summary.endedAt ?: nowMs
            )
            return ShiftSummaryModel(
                range = SummaryRange.of(summary.startedAt, summary.endedAt, nowMs, zone),
                totalMinor = day.totalMinor,
                connectedText = Formatters.durationHMSS(connectedSec),
                tripCount = day.tripCount,
                cashMinor = day.cashMinor,
                transferMinor = day.transferMinor,
                bars = platformBars(finished)
            )
        }

        private fun platformBars(finished: List<Trip>): List<PlatformBar> {
            val totals = finished.groupBy { it.platformId }.map { (platformId, trips) ->
                PlatformBar(
                    platformId = platformId,
                    name = trips.first().platformName,
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
