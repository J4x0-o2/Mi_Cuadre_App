package com.micuadre.app.presentation.home

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.ConnectedTime
import com.micuadre.app.domain.model.PeriodSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Figures of the "Resumen de hoy" block, derived from the today [PeriodSummary]. */
data class TodaySummaryModel(
    val totalMinor: Long,
    val cashMinor: Long,
    val transferMinor: Long,
    val tripCount: Int,
    val connectedSeconds: Long,
    /** Earnings per connected hour; null when nothing has been connected yet. */
    val perHourMinor: Long?,
    val sessionCount: Int
) {
    /** True once at least one shift started today. */
    val hasActivity: Boolean get() = sessionCount > 0

    companion object {
        private const val SECONDS_PER_HOUR = 3_600L

        /** Below one minute the tile reads "0:00"; a rate extrapolated from seconds is noise. */
        private const val MIN_SECONDS_FOR_RATE = 60L

        /** Nothing recorded yet; shown while the summary loads. */
        val EMPTY = TodaySummaryModel(0L, 0L, 0L, 0, 0L, null, 0)

        fun from(summary: PeriodSummary): TodaySummaryModel = of(
            totalMinor = summary.totalMinor,
            cashMinor = summary.cashMinor,
            transferMinor = summary.transferMinor,
            tripCount = summary.tripCount,
            connectedSeconds = summary.connectedSeconds,
            sessionCount = summary.sessionCount
        )

        /**
         * Figures of a paused shift that started on a previous day, from its own trips and
         * intervals as of [nowMs]. A paused shift normally has no open interval, but if one
         * is present it still counts up to [nowMs].
         */
        fun ofOpenShift(
            breakdown: DayBreakdown,
            intervals: List<ConnectedInterval>,
            nowMs: Long
        ): TodaySummaryModel = of(
            totalMinor = breakdown.totalMinor,
            cashMinor = breakdown.cashMinor,
            transferMinor = breakdown.transferMinor,
            tripCount = breakdown.tripCount,
            connectedSeconds = ConnectedTime.totalSeconds(intervals, nowMs = nowMs),
            sessionCount = 1
        )

        private fun of(
            totalMinor: Long,
            cashMinor: Long,
            transferMinor: Long,
            tripCount: Int,
            connectedSeconds: Long,
            sessionCount: Int
        ) = TodaySummaryModel(
            totalMinor = totalMinor,
            cashMinor = cashMinor,
            transferMinor = transferMinor,
            tripCount = tripCount,
            connectedSeconds = connectedSeconds,
            perHourMinor = if (connectedSeconds >= MIN_SECONDS_FOR_RATE) {
                (totalMinor * SECONDS_PER_HOUR + connectedSeconds / 2) / connectedSeconds
            } else {
                null
            },
            sessionCount = sessionCount
        )
    }
}

/** Subtitle under "Resumen de hoy": "Domingo 18 de octubre · 2 turnos". */
object TodaySubtitle {

    private val spanish = Locale.forLanguageTag("es")
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", spanish)

    fun format(date: LocalDate, sessionCount: Int): String {
        val day = dateFormat.format(date).replaceFirstChar { it.titlecase(spanish) }
        return when (sessionCount) {
            0 -> day
            1 -> "$day · 1 turno"
            else -> "$day · $sessionCount turnos"
        }
    }
}
