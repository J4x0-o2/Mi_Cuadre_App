package com.micuadre.app.domain.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Window an aggregate covers; every window ends today. */
enum class Period { TODAY, WEEK, MONTH }

/**
 * Concrete bounds of a [Period]: local dates [startDate]..[endDate] (today) and the matching
 * half-open epoch-millis interval [[fromMs], [toMs]) used to select sessions by their start time.
 */
data class PeriodRange(
    val period: Period,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val fromMs: Long,
    val toMs: Long
) {
    companion object {
        /** Range of [period] as seen at [nowMs] in [zone]. Weeks start on Monday. */
        fun of(period: Period, nowMs: Long, zone: ZoneId): PeriodRange {
            val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
            val start = when (period) {
                Period.TODAY -> today
                Period.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                Period.MONTH -> today.withDayOfMonth(1)
            }
            return PeriodRange(
                period = period,
                startDate = start,
                endDate = today,
                fromMs = start.atStartOfDay(zone).toInstant().toEpochMilli(),
                toMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            )
        }
    }
}
