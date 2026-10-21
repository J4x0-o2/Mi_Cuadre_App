package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodRange
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId
import javax.inject.Inject

/**
 * Streams the aggregate of every session started in [Period] (today, this week, this month),
 * recomputed whenever sessions, trips, connected intervals or the deduction amounts change; the
 * amounts of the same [from, to) range come from [deductions] so the shift repository stays about
 * trips and intervals. The caller supplies the
 * [ZoneId] (the one its own date ticker emitted) so the range and the ticker cannot disagree.
 */
class ObservePeriodSummaryUseCase @Inject constructor(
    private val repository: ShiftRepository,
    private val deductions: DeductionRepository,
    private val clock: Clock
) {
    operator fun invoke(period: Period, zone: ZoneId): Flow<PeriodSummary> {
        val range = PeriodRange.of(period, clock.nowMs(), zone)
        return combine(
            repository.observeSessionActivity(range.fromMs, range.toMs),
            deductions.observeAmountsStartedBetween(range.fromMs, range.toMs)
        ) { activity, amounts -> PeriodSummary.of(range, activity, clock.nowMs(), zone, amounts) }
    }
}
