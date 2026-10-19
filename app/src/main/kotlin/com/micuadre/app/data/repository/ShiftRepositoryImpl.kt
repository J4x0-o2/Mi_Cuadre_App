package com.micuadre.app.data.repository

import com.micuadre.app.data.local.dao.ConnectedIntervalDao
import com.micuadre.app.data.local.dao.SessionDao
import com.micuadre.app.data.local.dao.TripDao
import com.micuadre.app.data.local.entity.ConnectedIntervalEntity
import com.micuadre.app.data.local.entity.SessionEntity
import com.micuadre.app.data.local.entity.TripEntity
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.SessionActivity
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val tripDao: TripDao,
    private val intervalDao: ConnectedIntervalDao
) : ShiftRepository {

    override suspend fun createSession(startedAt: Long): Long =
        sessionDao.insert(SessionEntity(startedAt = startedAt, endedAt = null))

    override suspend fun endSession(sessionId: Long, endedAt: Long) {
        sessionDao.endSession(sessionId, endedAt)
        // An ended session never keeps an open interval.
        intervalDao.closeOpen(sessionId, endedAt)
    }

    override suspend fun openConnectedInterval(sessionId: Long, startedAt: Long) {
        intervalDao.openIfNone(sessionId, startedAt)
    }

    override suspend fun closeConnectedInterval(sessionId: Long, endedAt: Long) {
        intervalDao.closeOpen(sessionId, endedAt)
    }

    override fun observeConnectedIntervals(sessionId: Long): Flow<List<ConnectedInterval>> =
        intervalDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun activeSession(): Session? =
        sessionDao.activeSession()?.toDomain()

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> =
        tripDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun inProgressTrip(sessionId: Long): Trip? =
        tripDao.inProgressBySession(sessionId)?.toDomain()

    override suspend fun lastFinishedTrip(): Trip? =
        tripDao.lastFinished()?.toDomain()

    override fun observeSessionActivity(fromMs: Long, toMs: Long): Flow<List<SessionActivity>> =
        sessionDao.observeActivityStartedBetween(fromMs, toMs).map { rows ->
            rows.map { row ->
                SessionActivity(
                    session = row.session.toDomain(),
                    trips = row.trips.map { it.toDomain() },
                    intervals = row.intervals.map { it.toDomain() }
                )
            }
        }

    override suspend fun insertTrip(trip: Trip): Long =
        tripDao.insert(trip.toEntity())

    override suspend fun summary(sessionId: Long): DailySummary {
        val trips = tripDao.listBySession(sessionId)
        val session = sessionDao.byId(sessionId)
        val totalMinor = trips.sumOf { it.amountMinor ?: 0L }
        val count = trips.size
        val totalDurationMs = trips.sumOf {
            val end = it.endedAt ?: it.startedAt
            (end - it.startedAt).coerceAtLeast(0L)
        }
        val avg = if (count > 0) totalDurationMs / count else 0L
        return DailySummary(
            totalAmountMinor = totalMinor,
            tripCount = count,
            totalDurationMs = totalDurationMs,
            avgDurationMs = avg,
            startedAt = session?.startedAt,
            endedAt = session?.endedAt,
            trips = trips.map { it.toDomain() },
            connectedIntervals = intervalDao.listBySession(sessionId).map { it.toDomain() }
        )
    }

    override suspend fun totalMinor(sessionId: Long): Long =
        tripDao.sumAmountMinor(sessionId)

    override suspend fun finishTrip(
        sessionId: Long,
        platform: Platform,
        platformOtherLabel: String?,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        if (amountMinor <= 0L) return false
        if (endedAt <= startedAt) return false
        if (platform == Platform.OTHER && platformOtherLabel.isNullOrBlank()) return false

        val entity = TripEntity(
            sessionId = sessionId,
            platform = platform.name,
            platformOtherLabel = platformOtherLabel?.trim(),
            startedAt = startedAt,
            endedAt = endedAt,
            amountMinor = amountMinor,
            paymentMethod = paymentMethod.name
        )
        tripDao.insert(entity)
        return true
    }

    private fun ConnectedIntervalEntity.toDomain() =
        ConnectedInterval(id = id, sessionId = sessionId, startedAt = startedAt, endedAt = endedAt)

    private fun SessionEntity.toDomain() = Session(id = id, startedAt = startedAt, endedAt = endedAt)

    private fun TripEntity.toDomain() = Trip(
        id = id,
        sessionId = sessionId,
        platform = runCatching { Platform.valueOf(platform) }.getOrDefault(Platform.OTHER),
        platformOtherLabel = platformOtherLabel,
        startedAt = startedAt,
        endedAt = endedAt,
        amountMinor = amountMinor,
        paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }
    )

    private fun Trip.toEntity() = TripEntity(
        id = id,
        sessionId = sessionId,
        platform = platform.name,
        platformOtherLabel = platformOtherLabel,
        startedAt = startedAt,
        endedAt = endedAt,
        amountMinor = amountMinor,
        paymentMethod = paymentMethod?.name
    )
}
