package com.micuadre.app.presentation.period

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodBucket
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.util.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** One bar of the earnings chart. */
data class BarModel(
    /** Axis label: weekday initial for a week, day span ("5–11") for a month. */
    val label: String,
    /** Height relative to the tallest bar, 0..1. */
    val fraction: Float,
    /** Compact amount drawn above the bar; null for a bucket without earnings. */
    val valueText: String?,
    /** The bucket that holds today. */
    val isCurrent: Boolean,
    /** Spoken form so the bar is not color-only, e.g. "Viernes: $ 92.000". */
    val description: String
) {
    /** A bucket without earnings is drawn as a thin stub. */
    val isEmpty: Boolean get() = valueText == null
}

/**
 * Everything the week / month sheet shows, derived from a [PeriodSummary]. A null summary (still
 * loading) leaves every figure null and the chart empty, so the UI renders "—" placeholders.
 */
data class PeriodSheetModel(
    val period: Period,
    val rangeText: String,
    val totalMinor: Long?,
    val cashMinor: Long?,
    val transferMinor: Long?,
    val bars: List<BarModel>,
    val tripCount: Int?,
    val workedDays: Int?,
    /** Earnings per worked day; null while loading or when no day was worked. */
    val averageMinor: Long?
) {
    companion object {
        private val weekdayInitials = listOf("L", "M", "X", "J", "V", "S", "D")
        private val weekdayShort = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
        private val weekdayLong = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
        private val monthShort =
            listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

        /** [period] must be WEEK or MONTH; [today] is the local date the summary refers to. */
        fun from(period: Period, summary: PeriodSummary?, today: LocalDate?): PeriodSheetModel {
            require(period != Period.TODAY) { "The sheet only shows a week or a month" }
            val buckets = summary?.buckets.orEmpty()
            val max = buckets.maxOfOrNull { it.totalMinor } ?: 0L
            val average = summary?.takeIf { it.workedDays > 0 }
                ?.let { (it.totalMinor + it.workedDays / 2) / it.workedDays }
            return PeriodSheetModel(
                period = period,
                rangeText = today?.let { rangeText(period, it) }.orEmpty(),
                totalMinor = summary?.totalMinor,
                cashMinor = summary?.cashMinor,
                transferMinor = summary?.transferMinor,
                bars = buckets.map { bar(period, it, max, today) },
                tripCount = summary?.tripCount,
                workedDays = summary?.workedDays,
                averageMinor = average
            )
        }

        private fun rangeText(period: Period, today: LocalDate): String = when (period) {
            Period.WEEK -> {
                val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val end = start.plusDays(6)
                val startMonth = if (start.month == end.month) "" else " ${monthShort[start.monthValue - 1]}"
                "${weekdayShort[0]} ${start.dayOfMonth}$startMonth – ${weekdayShort[6]} ${end.dayOfMonth} " +
                    "${monthShort[end.monthValue - 1]} · semana actual"
            }
            else -> {
                val month = monthShort[today.monthValue - 1]
                if (today.dayOfMonth == 1) "1 $month · mes actual" else "1 – ${today.dayOfMonth} $month · mes actual"
            }
        }

        private fun bar(period: Period, bucket: PeriodBucket, max: Long, today: LocalDate?): BarModel {
            val first = bucket.startDate.dayOfMonth
            val last = bucket.endDate.dayOfMonth
            val (label, name) = when {
                period == Period.WEEK -> {
                    val i = bucket.startDate.dayOfWeek.value - 1
                    weekdayInitials[i] to weekdayLong[i]
                }
                bucket.startDate != bucket.endDate -> "$first–$last" to "Del $first al $last"
                else -> "$first" to "Día $first"
            }
            return BarModel(
                label = label,
                fraction = if (max > 0L) bucket.totalMinor.toFloat() / max else 0f,
                valueText = bucket.totalMinor.takeIf { it > 0L }?.let(Formatters::moneyCompact),
                isCurrent = today != null && today >= bucket.startDate && today <= bucket.endDate,
                description = "$name: ${Formatters.moneyNoDecimals(bucket.totalMinor)}"
            )
        }
    }
}
