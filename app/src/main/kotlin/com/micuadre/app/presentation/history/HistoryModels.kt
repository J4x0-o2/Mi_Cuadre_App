package com.micuadre.app.presentation.history

import com.micuadre.app.domain.model.HistoryAggregator
import com.micuadre.app.domain.model.HistoryWeek
import com.micuadre.app.domain.model.MonthDetail
import com.micuadre.app.domain.model.MonthHistory
import com.micuadre.app.domain.model.YearHistory
import com.micuadre.app.util.Formatters
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es")
private val monthNameFormat = DateTimeFormatter.ofPattern("MMMM", spanish)
private val monthShort = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

/** "Octubre": the Spanish name of [month], first letter in uppercase. */
internal fun monthName(month: YearMonth): String =
    monthNameFormat.format(month).replaceFirstChar { it.titlecase(spanish) }

/** One month card of the year screen. */
data class MonthRowModel(
    val month: YearMonth,
    val name: String,
    /** The month of today: the card reads "Octubre · en curso". */
    val isCurrent: Boolean,
    val grossText: String,
    /** Gross relative to the best month of the year, 0..1. */
    val barFraction: Float,
    val netText: String,
    val netNegative: Boolean,
    val shiftCount: Int
)

/** Everything the year screen shows, ready to draw. */
data class YearHistoryModel(
    val year: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val grossText: String,
    val netText: String,
    val netNegative: Boolean,
    val shiftCount: Int,
    val months: List<MonthRowModel>
) {
    /** The year has no shifts: the screen shows its empty state instead of the list. */
    val isEmpty: Boolean get() = months.isEmpty()

    companion object {
        fun from(history: YearHistory): YearHistoryModel {
            val best = history.months.maxOfOrNull { it.totals.grossMinor } ?: 0L
            return YearHistoryModel(
                year = history.year,
                canGoBack = history.hasPreviousYear,
                canGoForward = !history.isCurrentYear,
                grossText = Formatters.moneyNoDecimals(history.totals.grossMinor),
                netText = Formatters.signedMoney(history.totals.netMinor),
                netNegative = history.totals.netMinor < 0L,
                shiftCount = history.totals.shiftCount,
                months = history.months.map { row(it, best) }
            )
        }

        private fun row(month: MonthHistory, best: Long) = MonthRowModel(
            month = month.month,
            name = monthName(month.month),
            isCurrent = month.isCurrent,
            grossText = Formatters.moneyNoDecimals(month.totals.grossMinor),
            barFraction = HistoryAggregator.barFraction(month.totals.grossMinor, best),
            netText = Formatters.signedMoney(month.totals.netMinor),
            netNegative = month.totals.netMinor < 0L,
            shiftCount = month.totals.shiftCount
        )
    }
}

/** One week card of the month screen. */
data class WeekRowModel(
    /** "1 – 7 sep", "30 sep" for a one day week, "29 sep – 5 oct" across two months. */
    val rangeText: String,
    val grossText: String,
    /** Gross relative to the best week of the month, 0..1. */
    val barFraction: Float,
    val netText: String,
    val netNegative: Boolean,
    val shiftCount: Int
)

/** Everything the month screen shows, ready to draw. */
data class MonthHistoryModel(
    val month: YearMonth,
    val title: String,
    val year: Int,
    val grossText: String,
    val expensesText: String,
    val netText: String,
    val netNegative: Boolean,
    val shiftCount: Int,
    val weeks: List<WeekRowModel>
) {
    companion object {
        fun from(detail: MonthDetail): MonthHistoryModel {
            val best = detail.weeks.maxOfOrNull { it.totals.grossMinor } ?: 0L
            return MonthHistoryModel(
                month = detail.month,
                title = monthName(detail.month),
                year = detail.month.year,
                grossText = Formatters.moneyNoDecimals(detail.totals.grossMinor),
                expensesText = Formatters.expenseMoney(detail.totals.expensesMinor),
                netText = Formatters.signedMoney(detail.totals.netMinor),
                netNegative = detail.totals.netMinor < 0L,
                shiftCount = detail.totals.shiftCount,
                weeks = detail.weeks.map { row(it, best) }
            )
        }

        private fun row(week: HistoryWeek, best: Long) = WeekRowModel(
            rangeText = range(week.startDate, week.endDate),
            grossText = Formatters.moneyNoDecimals(week.totals.grossMinor),
            barFraction = HistoryAggregator.barFraction(week.totals.grossMinor, best),
            netText = Formatters.signedMoney(week.totals.netMinor),
            netNegative = week.totals.netMinor < 0L,
            shiftCount = week.totals.shiftCount
        )

        private fun range(start: LocalDate, end: LocalDate): String {
            val endText = "${end.dayOfMonth} ${monthShort[end.monthValue - 1]}"
            return when {
                start == end -> endText
                start.month == end.month -> "${start.dayOfMonth} – $endText"
                else -> "${start.dayOfMonth} ${monthShort[start.monthValue - 1]} – $endText"
            }
        }
    }
}
