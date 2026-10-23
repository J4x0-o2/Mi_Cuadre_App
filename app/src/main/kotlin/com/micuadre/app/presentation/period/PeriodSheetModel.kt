package com.micuadre.app.presentation.period

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodBucket
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.util.Formatters
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** What the period spent on one deduction. */
data class CostLine(val name: String, val amountMinor: Long)

/** One bar of the earnings chart. */
data class BarModel(
    /** Axis label: weekday initial for a week, day span ("5–11") for a month. */
    val label: String,
    /** Total height relative to the tallest bar, 0..1 (net + costs when the period has costs). */
    val fraction: Float,
    /** Net part of a stacked bar, 0..1; equals [fraction] when the period has no costs. */
    val netFraction: Float,
    /** Costs part of a stacked bar, 0..1; 0 when the bucket has no costs. */
    val costFraction: Float,
    /** The bucket spent more than it earned, so the bar is clamped to its costs. */
    val costsExceedGross: Boolean,
    /** Compact amount drawn above the bar; null for a bucket without earnings. */
    val valueText: String?,
    /** The bucket that holds today. */
    val isCurrent: Boolean,
    /** Spoken form so the bar is not color-only, e.g. "Viernes: $ 92.000". */
    val description: String
) {
    /** A bucket without earnings nor costs is drawn as a thin stub. */
    val isEmpty: Boolean get() = valueText == null && costFraction == 0f
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
    val averageMinor: Long?,
    /** Per-deduction totals of the period, ordered by deduction id; empty without costs. */
    val costLines: List<CostLine> = emptyList(),
    val costsMinor: Long = 0L,
    /** Gross minus costs, negative when the period spent more than it earned; null while loading. */
    val netMinor: Long? = null,
    /** Share of the gross kept / spent (0..100, adding up to 100); null without costs or without gross. */
    val netPercent: Int? = null,
    val costsPercent: Int? = null,
    /** Net per worked day, rounded like [averageMinor]; null without costs or worked days. */
    val netPerDayMinor: Long? = null
) {
    /**
     * Whether the period has expenses. Without them the sheet keeps its gross-only look: no
     * "Gastos y neto" card, single-color bars and no NETO/DÍA tile.
     */
    val hasCosts: Boolean get() = costsMinor > 0L

    /** Some bar is clamped to its costs; the chart explains it. */
    val hasBarOverrun: Boolean get() = bars.any { it.costsExceedGross }

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
            // A bar is as tall as its gross, or as its costs when those are bigger.
            val max = buckets.maxOfOrNull { maxOf(it.totalMinor, it.costsMinor) } ?: 0L
            val average = summary?.takeIf { it.workedDays > 0 }
                ?.let { (it.totalMinor + it.workedDays / 2) / it.workedDays }
            val costs = summary?.costsMinor ?: 0L
            val net = summary?.let { it.totalMinor - costs }
            val ratio = summary?.takeIf { costs > 0L && it.totalMinor > 0L }
                ?.let { ((costs * 100 + it.totalMinor / 2) / it.totalMinor).coerceIn(0L, 100L).toInt() }
            return PeriodSheetModel(
                period = period,
                rangeText = today?.let { rangeText(period, it) }.orEmpty(),
                totalMinor = summary?.totalMinor,
                cashMinor = summary?.cashMinor,
                transferMinor = summary?.transferMinor,
                bars = buckets.map { bar(period, it, max, today) },
                tripCount = summary?.tripCount,
                workedDays = summary?.workedDays,
                averageMinor = average,
                costLines = summary?.costs.orEmpty().map { CostLine(it.name, it.totalMinor) },
                costsMinor = costs,
                netMinor = net,
                netPercent = ratio?.let { 100 - it },
                costsPercent = ratio,
                netPerDayMinor = if (costs > 0L && net != null) summary.workedDays.takeIf { it > 0 }
                    ?.let { roundedDiv(net, it) } else null
            )
        }

        /** Divides rounding half away from zero, so a negative net rounds symmetrically. */
        private fun roundedDiv(value: Long, by: Int): Long =
            if (value >= 0L) (value + by / 2) / by else -((-value + by / 2) / by)

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
            val gross = bucket.totalMinor
            val costs = bucket.costsMinor
            fun share(value: Long) = if (max > 0L) value.toFloat() / max else 0f
            val netFraction = share((gross - costs).coerceAtLeast(0L))
            val costFraction = share(costs)
            val description = buildString {
                append("$name: ${Formatters.moneyNoDecimals(gross)}")
                if (costs > 0L) {
                    append(", gastos ${Formatters.moneyNoDecimals(costs)}")
                    append(", neto ${Formatters.signedMoney(gross - costs)}")
                }
            }
            return BarModel(
                label = label,
                fraction = netFraction + costFraction,
                netFraction = netFraction,
                costFraction = costFraction,
                costsExceedGross = costs > gross,
                valueText = gross.takeIf { it > 0L }?.let(Formatters::moneyCompact),
                isCurrent = today != null && today >= bucket.startDate && today <= bucket.endDate,
                description = description
            )
        }
    }
}
