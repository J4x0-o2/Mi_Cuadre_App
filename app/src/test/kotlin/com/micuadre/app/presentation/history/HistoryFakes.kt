package com.micuadre.app.presentation.history

import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.SessionActivity
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import java.time.ZoneId

/** Shared test doubles for the History tests: the read side of the repositories over fixed data. */
internal class FakeHistoryShifts(private val activity: List<SessionActivity>) : ShiftRepository {

    override fun observeSessionActivity(fromMs: Long, toMs: Long): Flow<List<SessionActivity>> =
        flowOf(activity.filter { it.session.startedAt in fromMs until toMs })

    override fun observeEarliestSessionStart(): Flow<Long?> =
        flowOf(activity.minOfOrNull { it.session.startedAt })

    override suspend fun createSession(startedAt: Long): Long = error("unused")
    override suspend fun endSession(sessionId: Long, endedAt: Long) = error("unused")
    override suspend fun activeSession(): Session? = error("unused")
    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
    override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
    override suspend fun insertTrip(trip: Trip): Long = error("unused")
    override suspend fun cancelInProgressTrip(sessionId: Long) = error("unused")
    override suspend fun summary(sessionId: Long): DailySummary = error("unused")
    override suspend fun totalMinor(sessionId: Long): Long = error("unused")
    override suspend fun openTrip(sessionId: Long, startedAt: Long): Trip = error("unused")
    override suspend fun finishOpenTrip(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean = error("unused")
}

internal class FakeHistoryDeductions(private val amounts: List<SessionDeductionAmount> = emptyList()) :
    DeductionRepository {

    override fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>> =
        flowOf(amounts)

    override suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount> = error("unused")
    override fun observeActive(): Flow<List<Deduction>> = flowOf(emptyList())
    override fun observeVisible(): Flow<List<Deduction>> = flowOf(emptyList())
    override suspend fun create(name: String, usualAmountMinor: Long?): DeductionResult = error("unused")
    override suspend fun rename(id: Long, name: String): DeductionResult = error("unused")
    override suspend fun update(id: Long, name: String, usualAmountMinor: Long?): DeductionResult = error("unused")
    override suspend fun setVisible(id: Long, visible: Boolean): DeductionResult = error("unused")
    override suspend fun setUsualAmount(id: Long, amountMinor: Long?): DeductionResult = error("unused")
    override suspend fun delete(id: Long): DeductionResult = error("unused")
    override suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>): DeductionResult =
        error("unused")
}

internal val bogota: ZoneId = ZoneId.of("America/Bogota")

internal fun msOf(date: String, time: String = "00:00:00"): Long =
    LocalDateTime.parse("${date}T$time").atZone(bogota).toInstant().toEpochMilli()

/** A shift started on [date] with one finished trip of [gross]; the id doubles as the trip id. */
internal fun shiftOn(id: Long, date: String, gross: Long): SessionActivity {
    val start = msOf(date, "20:00:00")
    return SessionActivity(
        session = Session(id = id, startedAt = start, endedAt = start + 7_200_000L),
        trips = listOf(
            Trip(
                id = id, sessionId = id, platformId = 1L, platformName = "Didi", startedAt = start,
                endedAt = start + 600_000L, amountMinor = gross, paymentMethod = PaymentMethod.CASH
            )
        ),
        intervals = emptyList()
    )
}
