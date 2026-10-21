package com.micuadre.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A session with the trips and connected intervals that belong to it. */
data class SessionActivity(
    val session: Session,
    val trips: List<Trip>,
    val intervals: List<ConnectedInterval>
)

/** What a period spent on one deduction (name as currently stored, deleted deductions included). */
data class DeductionTotal(
    val deductionId: Long,
    val name: String,
    val totalMinor: Long
)

/**
 * Earnings ([totalMinor], gross) and expenses ([costsMinor]) of the local dates
 * [startDate]..[endDate] (inclusive); one day for week buckets.
 */
data class PeriodBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalMinor: Long,
    val costsMinor: Long = 0L
)

/**
 * Aggregate of every session that STARTED inside a [PeriodRange]: a session counts entirely for
 * the local date it started, so a night shift is never split at midnight. Only finished trips
 * count. [buckets] are the 7 days of the week, the calendar-week segments of the month, or
 * empty for today. The deduction amounts of a session follow the session: they are attributed to
 * the same day and bucket as its trips. [totalMinor] stays gross; [costs] (ordered by deduction id)
 * and [costsMinor] hold the expenses.
 */
data class PeriodSummary(
    val totalMinor: Long,
    val cashMinor: Long,
    val transferMinor: Long,
    val tripCount: Int,
    val connectedSeconds: Long,
    val sessionCount: Int,
    val workedDays: Int,
    val firstSessionStartMs: Long?,
    val buckets: List<PeriodBucket>,
    val costs: List<DeductionTotal> = emptyList()
) {
    /** Everything the period spent on deductions. */
    val costsMinor: Long get() = costs.sumOf { it.totalMinor }

    companion object {
        /**
         * Aggregates [sessions] (those started outside [range] are ignored) as of [nowMs]. [amounts]
         * are the deduction amounts of those sessions; the ones of sessions outside [range] are ignored.
         */
        fun of(
            range: PeriodRange,
            sessions: List<SessionActivity>,
            nowMs: Long,
            zone: ZoneId,
            amounts: List<SessionDeductionAmount> = emptyList()
        ): PeriodSummary {
            val inRange = sessions.filter { it.session.startedAt in range.fromMs until range.toMs }
            val finished = inRange.flatMap { it.trips }.filter { it.endedAt != null }
            fun sum(method: PaymentMethod) =
                finished.filter { it.paymentMethod == method }.sumOf { it.amountMinor ?: 0L }

            val totalByDate = inRange
                .groupBy { startDate(it, zone) }
                .mapValues { (_, day) ->
                    day.sumOf { s -> s.trips.filter { it.endedAt != null }.sumOf { it.amountMinor ?: 0L } }
                }

            val dateBySession = inRange.associate { it.session.id to startDate(it, zone) }
            val inRangeAmounts = amounts.filter { it.sessionId in dateBySession }
            val costByDate = inRangeAmounts
                .groupingBy { dateBySession.getValue(it.sessionId) }
                .fold(0L) { sum, amount -> sum + amount.amountMinor }

            return PeriodSummary(
                totalMinor = finished.sumOf { it.amountMinor ?: 0L },
                cashMinor = sum(PaymentMethod.CASH),
                transferMinor = sum(PaymentMethod.TRANSFER),
                tripCount = finished.size,
                connectedSeconds = inRange.sumOf { ConnectedTime.totalSeconds(it.intervals, nowMs) },
                sessionCount = inRange.size,
                workedDays = totalByDate.size,
                firstSessionStartMs = inRange.minOfOrNull { it.session.startedAt },
                buckets = buckets(range, totalByDate, costByDate),
                costs = inRangeAmounts.groupBy { it.deductionId }.toSortedMap().map { (id, rows) ->
                    DeductionTotal(id, rows.first().name, rows.sumOf { it.amountMinor })
                }
            )
        }

        private fun startDate(activity: SessionActivity, zone: ZoneId): LocalDate =
            Instant.ofEpochMilli(activity.session.startedAt).atZone(zone).toLocalDate()

        private fun buckets(
            range: PeriodRange,
            totalByDate: Map<LocalDate, Long>,
            costByDate: Map<LocalDate, Long>
        ): List<PeriodBucket> {
            fun sum(byDate: Map<LocalDate, Long>, from: LocalDate, to: LocalDate) =
                byDate.filterKeys { it >= from && it <= to }.values.sum()
            fun bucket(from: LocalDate, to: LocalDate) =
                PeriodBucket(from, to, sum(totalByDate, from, to), sum(costByDate, from, to))

            return when (range.period) {
                Period.TODAY -> emptyList()
                // Always the full Monday..Sunday week; days after today simply hold zero.
                Period.WEEK -> (0L until 7L).map { range.startDate.plusDays(it) }
                    .map { bucket(it, it) }
                // Segments break after each Sunday and are clipped to the month and to today.
                Period.MONTH -> generateSequence(range.startDate) { segmentEnd(it, range).plusDays(1) }
                    .takeWhile { it <= range.endDate }
                    .map { start -> bucket(start, segmentEnd(start, range)) }
                    .toList()
            }
        }

        private fun segmentEnd(start: LocalDate, range: PeriodRange): LocalDate {
            val sunday = start.plusDays((DayOfWeek.SUNDAY.value - start.dayOfWeek.value).toLong())
            return minOf(sunday, range.endDate)
        }
    }
}
