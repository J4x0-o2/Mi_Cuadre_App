package com.micuadre.app.data.repository

import com.micuadre.app.data.local.dao.SessionDao
import com.micuadre.app.data.local.dao.TripDao
import com.micuadre.app.data.local.entity.SessionEntity
import com.micuadre.app.data.local.entity.TripEntity
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    private val tripDao: TripDao
) : ShiftRepository {

    override suspend fun createSession(startedAt: Long): Long =
        sessionDao.insert(SessionEntity(startedAt = startedAt, endedAt = null))

    override suspend fun endSession(sessionId: Long, endedAt: Long) {
        sessionDao.endSession(sessionId, endedAt)
    }

    override suspend fun activeSession(): Session? =
        sessionDao.activeSession()?.toDomain()

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> =
        tripDao.observeBySession(sessionId).map { list -> list.map { it.toDomain() } }

    override suspend fun inProgressTrip(sessionId: Long): Trip? =
        tripDao.inProgressBySession(sessionId)?.toDomain()

    override suspend fun insertTrip(trip: Trip): Long =
        tripDao.insert(trip.toEntity())

    override suspend fun summary(sessionId: Long): DailySummary {
        val trips = tripDao.listBySession(sessionId)
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
            avgDurationMs = avg
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
