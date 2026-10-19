package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PeriodRange
import com.micuadre.app.domain.model.PeriodSummary
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.util.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Provider

/**
 * Streams the aggregate of every session started in [Period] (today, this week, this month),
 * recomputed whenever sessions, trips or connected intervals change. The time zone is read on
 * every call so a device zone change applies to the next subscription.
 */
class ObservePeriodSummaryUseCase @Inject constructor(
    private val repository: ShiftRepository,
    private val clock: Clock,
    private val zone: Provider<ZoneId>
) {
    operator fun invoke(period: Period): Flow<PeriodSummary> {
        val zone = zone.get()
        val range = PeriodRange.of(period, clock.nowMs(), zone)
        return repository.observeSessionActivity(range.fromMs, range.toMs)
            .map { PeriodSummary.of(range, it, clock.nowMs(), zone) }
    }
}
