package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.HistoryAggregator
import com.micuadre.app.domain.model.MonthDetail
import com.micuadre.app.domain.model.YearHistory
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

private fun startMs(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

/**
 * Streams the history of a calendar year: its totals and its months with shifts, recomputed when
 * sessions, trips or deduction amounts change. The caller supplies the [ZoneId] so the dates it
 * shows and the ones grouped here cannot disagree.
 */
class ObserveYearHistoryUseCase @Inject constructor(
    private val repository: ShiftRepository,
    private val deductions: DeductionRepository,
    private val clock: Clock
) {
    operator fun invoke(year: Int, zone: ZoneId): Flow<YearHistory> {
        val fromMs = startMs(LocalDate.of(year, 1, 1), zone)
        val toMs = startMs(LocalDate.of(year + 1, 1, 1), zone)
        return combine(
            repository.observeSessionActivity(fromMs, toMs),
            deductions.observeAmountsStartedBetween(fromMs, toMs),
            repository.observeEarliestSessionStart()
        ) { activity, amounts, earliest ->
            HistoryAggregator.year(year, activity, amounts, earliest, clock.nowMs(), zone)
        }
    }
}

/** Streams the totals and weeks of one [YearMonth], recomputed like [ObserveYearHistoryUseCase]. */
class ObserveMonthHistoryUseCase @Inject constructor(
    private val repository: ShiftRepository,
    private val deductions: DeductionRepository,
    private val clock: Clock
) {
    operator fun invoke(month: YearMonth, zone: ZoneId): Flow<MonthDetail> {
        val fromMs = startMs(month.atDay(1), zone)
        val toMs = startMs(month.plusMonths(1).atDay(1), zone)
        return combine(
            repository.observeSessionActivity(fromMs, toMs),
            deductions.observeAmountsStartedBetween(fromMs, toMs)
        ) { activity, amounts -> HistoryAggregator.month(month, activity, amounts, clock.nowMs(), zone) }
    }
}
