package com.micuadre.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * What a slice of history earned and spent. [grossMinor] counts finished trips only, [expensesMinor]
 * the deduction amounts of the same shifts and [shiftCount] every shift started in the slice
 * (the live one and shifts without trips included, like [PeriodSummary.sessionCount]).
 */
data class HistoryTotals(
    val grossMinor: Long,
    val expensesMinor: Long,
    val shiftCount: Int
) {
    /** Gross minus expenses; negative when a shift cost more than it earned. */
    val netMinor: Long get() = grossMinor - expensesMinor
}

/** One calendar [month] that has at least one shift. [isCurrent] marks the month of today. */
data class MonthHistory(
    val month: YearMonth,
    val totals: HistoryTotals,
    val isCurrent: Boolean
)

/**
 * A [year] of history. [months] only holds months with shifts, newest first. [hasPreviousYear] is
 * true when any shift started before January of [year]; [isCurrentYear] disables stepping forward.
 */
data class YearHistory(
    val year: Int,
    val totals: HistoryTotals,
    val months: List<MonthHistory>,
    val hasPreviousYear: Boolean,
    val isCurrentYear: Boolean
)

/** A Monday..Sunday segment of a month, clipped to the month: [startDate]..[endDate] inclusive. */
data class HistoryWeek(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totals: HistoryTotals
)

/**
 * One [month] with its [totals] and its [weeks] in calendar order. Past months list every week,
 * zero weeks included, so the calendar reads continuously; the current month lists the weeks up to
 * the one that contains today (that week is whole, not cut at today).
 */
data class MonthDetail(
    val month: YearMonth,
    val totals: HistoryTotals,
    val weeks: List<HistoryWeek>
)

/**
 * Pure grouping behind the History screens. Like [PeriodSummary], a shift belongs entirely to the
 * local date of its start (in [ZoneId]), so a night shift is never split at midnight or at a month
 * boundary, and its deduction amounts follow it. Weeks start on Monday.
 */
object HistoryAggregator {

    /** A shift reduced to what the history needs: its start date and money. */
    private data class Shift(val date: LocalDate, val grossMinor: Long, val expensesMinor: Long)

    /**
     * History of [year] from [sessions] and their deduction [amounts] (anything outside the year is
     * ignored). [earliestStartMs] is the start of the oldest session overall, null when none.
     */
    fun year(
        year: Int,
        sessions: List<SessionActivity>,
        amounts: List<SessionDeductionAmount>,
        earliestStartMs: Long?,
        nowMs: Long,
        zone: ZoneId
    ): YearHistory {
        val today = dateOf(nowMs, zone)
        val shifts = shifts(sessions, amounts, zone).filter { it.date.year == year }
        val months = shifts
            .groupBy { YearMonth.from(it.date) }
            .map { (month, rows) -> MonthHistory(month, totals(rows), isCurrent = month == YearMonth.from(today)) }
            .sortedByDescending { it.month }
        return YearHistory(
            year = year,
            totals = totals(shifts),
            months = months,
            hasPreviousYear = earliestStartMs != null && dateOf(earliestStartMs, zone).year < year,
            isCurrentYear = year == today.year
        )
    }

    /** Detail of [month]: its totals and its weeks (see [MonthDetail] for which weeks are listed). */
    fun month(
        month: YearMonth,
        sessions: List<SessionActivity>,
        amounts: List<SessionDeductionAmount>,
        nowMs: Long,
        zone: ZoneId
    ): MonthDetail {
        val today = dateOf(nowMs, zone)
        val shifts = shifts(sessions, amounts, zone).filter { YearMonth.from(it.date) == month }
        val weeks = generateSequence(month.atDay(1)) { weekEnd(it, month).plusDays(1) }
            .takeWhile { it <= month.atEndOfMonth() && it <= today }
            .map { start ->
                val end = weekEnd(start, month)
                HistoryWeek(start, end, totals(shifts.filter { it.date in start..end }))
            }
            .toList()
        return MonthDetail(month, totals(shifts), weeks)
    }

    /** Length of a bar as a 0..1 fraction of the best value [max]; 0 when [max] is not positive. */
    fun barFraction(value: Long, max: Long): Float =
        if (max <= 0L) 0f else (value.toFloat() / max).coerceIn(0f, 1f)

    private fun dateOf(ms: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** The Sunday that closes the week of [start], or the last day of [month] when that comes first. */
    private fun weekEnd(start: LocalDate, month: YearMonth): LocalDate =
        minOf(start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), month.atEndOfMonth())

    private fun shifts(
        sessions: List<SessionActivity>,
        amounts: List<SessionDeductionAmount>,
        zone: ZoneId
    ): List<Shift> {
        val expensesBySession = amounts.groupBy { it.sessionId }.mapValues { (_, rows) -> rows.sumOf { it.amountMinor } }
        return sessions.map { activity ->
            Shift(
                date = dateOf(activity.session.startedAt, zone),
                grossMinor = activity.trips.filter { it.endedAt != null }.sumOf { it.amountMinor ?: 0L },
                expensesMinor = expensesBySession[activity.session.id] ?: 0L
            )
        }
    }

    private fun totals(shifts: List<Shift>) =
        HistoryTotals(shifts.sumOf { it.grossMinor }, shifts.sumOf { it.expensesMinor }, shifts.size)
}
