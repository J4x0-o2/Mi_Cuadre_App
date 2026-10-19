package com.micuadre.app.presentation.home

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

        /** Nothing recorded yet; shown while the summary loads. */
        val EMPTY = TodaySummaryModel(0L, 0L, 0L, 0, 0L, null, 0)

        fun from(summary: PeriodSummary): TodaySummaryModel {
            val seconds = summary.connectedSeconds
            return TodaySummaryModel(
                totalMinor = summary.totalMinor,
                cashMinor = summary.cashMinor,
                transferMinor = summary.transferMinor,
                tripCount = summary.tripCount,
                connectedSeconds = seconds,
                perHourMinor = if (seconds > 0L) {
                    (summary.totalMinor * SECONDS_PER_HOUR + seconds / 2) / seconds
                } else {
                    null
                },
                sessionCount = summary.sessionCount
            )
        }
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
