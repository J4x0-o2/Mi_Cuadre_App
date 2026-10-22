package com.micuadre.app.data.repository

import com.micuadre.app.data.local.dao.ConnectedIntervalDao
import com.micuadre.app.data.local.dao.PlatformDao
import com.micuadre.app.data.local.dao.SessionDao
import com.micuadre.app.data.local.dao.TripDao
import com.micuadre.app.data.local.entity.ConnectedIntervalEntity
import com.micuadre.app.data.local.entity.PlatformEntity
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val tripDao: TripDao,
    private val intervalDao: ConnectedIntervalDao,
    private val platformDao: PlatformDao
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
        combine(tripDao.observeBySession(sessionId), platformDao.observeAll()) { trips, platforms ->
            val names = platforms.nameById()
            trips.mapNotNull { it.toClosedDomain(names) }
        }

    override suspend fun inProgressTrip(sessionId: Long): Trip? =
        tripDao.inProgressBySession(sessionId)?.toOpenDomain()

    override suspend fun lastFinishedTrip(): Trip? =
        tripDao.lastFinished()?.toClosedDomain(platformNames())

    override fun observeVisiblePlatforms(): Flow<List<Platform>> =
        platformDao.observeVisible().map { list -> list.map { it.toDomain() } }

    override fun observeSessionActivity(fromMs: Long, toMs: Long): Flow<List<SessionActivity>> =
        combine(sessionDao.observeActivityStartedBetween(fromMs, toMs), platformDao.observeAll()) { rows, platforms ->
            val names = platforms.nameById()
            rows.map { row ->
                SessionActivity(
                    session = row.session.toDomain(),
                    // A @Relation cannot filter in SQL, so open trips are dropped here.
                    trips = row.trips.mapNotNull { it.toClosedDomain(names) },
                    intervals = row.intervals.map { it.toDomain() }
                )
            }
        }

    override suspend fun cancelInProgressTrip(sessionId: Long) {
        tripDao.deleteInProgressBySession(sessionId)
    }

    override suspend fun openTrip(sessionId: Long, startedAt: Long): Long {
        val id = tripDao.insertOpenIfNone(sessionId, startedAt)
        if (id > 0L) return id
        // The session already had an open trip: keep it.
        return checkNotNull(tripDao.inProgressBySession(sessionId)) { "open trip vanished" }.id
    }

    override suspend fun finishOpenTrip(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        if (amountMinor <= 0L) return false
        return tripDao.finishOpen(tripId, platformId, endedAt, amountMinor, paymentMethod.name) > 0
    }

    override suspend fun insertTrip(trip: Trip): Long =
        tripDao.insert(trip.toEntity())

    private suspend fun platformNames(): Map<Long, String> = platformDao.listAll().nameById()

    override suspend fun summary(sessionId: Long): DailySummary {
        val names = platformNames()
        val entities = tripDao.listBySession(sessionId)
        val trips = entities.filter { it.platformId != null }
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
            trips = trips.mapNotNull { it.toClosedDomain(names) },
            connectedIntervals = intervalDao.listBySession(sessionId).map { it.toDomain() }
        )
    }

    override suspend fun totalMinor(sessionId: Long): Long =
        tripDao.sumAmountMinor(sessionId)

    override suspend fun finishTrip(
        sessionId: Long,
        platformId: Long,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        if (amountMinor <= 0L) return false
        if (endedAt <= startedAt) return false
        // The platform check lives in the insert itself, so it cannot go stale before the write.
        return tripDao.insertIfPlatformActive(
            sessionId = sessionId,
            platformId = platformId,
            startedAt = startedAt,
            endedAt = endedAt,
            amountMinor = amountMinor,
            paymentMethod = paymentMethod.name
        ) > 0L
    }

    private fun ConnectedIntervalEntity.toDomain() =
        ConnectedInterval(id = id, sessionId = sessionId, startedAt = startedAt, endedAt = endedAt)

    private fun SessionEntity.toDomain() = Session(id = id, startedAt = startedAt, endedAt = endedAt)

    private fun PlatformEntity.toDomain() =
        Platform(id = id, name = name, visible = visible, createdAt = createdAt, deletedAt = deletedAt)

    private fun List<PlatformEntity>.nameById(): Map<Long, String> = associate { it.id to it.name }

    /**
     * Maps a finished trip. Returns null for an open trip (no end) and for a row without a platform,
     * so open rows can never reach code that expects closed trips.
     */
    private fun TripEntity.toClosedDomain(names: Map<Long, String>): Trip? {
        if (endedAt == null) return null
        val platform = platformId ?: return null
        return Trip(
            id = id,
            sessionId = sessionId,
            platformId = platform,
            // The foreign key guarantees the row exists; the fallback only guards a torn read.
            platformName = names[platform].orEmpty(),
            startedAt = startedAt,
            endedAt = endedAt,
            amountMinor = amountMinor,
            paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }
        )
    }

    /**
     * Maps the open trip. The domain model has no "no platform" value, so an open trip carries
     * platformId 0 (never a real id) and an empty name until it is finished.
     */
    private suspend fun TripEntity.toOpenDomain() = Trip(
        id = id,
        sessionId = sessionId,
        platformId = platformId ?: 0L,
        platformName = platformId?.let { platformNames()[it] }.orEmpty(),
        startedAt = startedAt,
        endedAt = endedAt,
        amountMinor = amountMinor,
        paymentMethod = paymentMethod?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }
    )

    private fun Trip.toEntity() = TripEntity(
        id = id,
        sessionId = sessionId,
        // 0 is the domain marker for "no platform yet"; it is stored as NULL.
        platformId = platformId.takeIf { it > 0L },
        startedAt = startedAt,
        endedAt = endedAt,
        amountMinor = amountMinor,
        paymentMethod = paymentMethod?.name
    )
}
