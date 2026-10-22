package com.micuadre.app.domain.repository

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.SessionActivity
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Repository contract for shift + trip persistence.
 * Implementations translate between Room entities and domain models.
 */
interface ShiftRepository {

    /** Inserts a new session row, returning its generated id. */
    suspend fun createSession(startedAt: Long): Long

    /** Marks the given session as ended at [endedAt]. */
    suspend fun endSession(sessionId: Long, endedAt: Long)

    /** Returns the most recent session row that has not been ended, if any. */
    suspend fun activeSession(): Session?

    /** Streams all trips for a session, newest first. */
    fun observeTrips(sessionId: Long): Flow<List<Trip>>

    /**
     * Returns the in-progress trip for [sessionId] (a row whose `endedAt` is
     * null), or null if there is none. Used by the service on rehydration
     * after the OS has killed and recreated it.
     */
    suspend fun inProgressTrip(sessionId: Long): Trip? = null

    /** Most recently finished trip across all sessions, or null if none. */
    suspend fun lastFinishedTrip(): Trip? = null

    /**
     * Opens a connected interval for [sessionId] starting at [startedAt], unless the session
     * already has an open one (idempotent). Defaults to a no-op for repositories without intervals.
     */
    suspend fun openConnectedInterval(sessionId: Long, startedAt: Long) {}

    /** Closes the session's open connected interval, if any, at [endedAt]. */
    suspend fun closeConnectedInterval(sessionId: Long, endedAt: Long) {}

    /** Streams the session's connected intervals, oldest first. */
    fun observeConnectedIntervals(sessionId: Long): Flow<List<ConnectedInterval>> = flowOf(emptyList())

    /**
     * Streams every session that STARTED in [[fromMs], [toMs]) with its trips and connected
     * intervals, oldest session first. Selection is by start time only, so a session that crosses
     * midnight stays whole with the day it started.
     */
    fun observeSessionActivity(fromMs: Long, toMs: Long): Flow<List<SessionActivity>> = flowOf(emptyList())

    /**
     * Streams the platforms offered when finishing a trip: not deleted and visible, in creation
     * order.
     */
    fun observeVisiblePlatforms(): Flow<List<Platform>>

    /**
     * Discards the in-progress trip of [sessionId] (the row whose `endedAt` is null), if any.
     * Finished trips are never touched, so totals do not change.
     */
    suspend fun cancelInProgressTrip(sessionId: Long)

    /**
     * Persists a ride in progress for [sessionId] starting at [startedAt] (no amount, payment or
     * platform yet) and returns the stored row, read back in the same transaction that inserted it.
     * Idempotent: when the session already has an open trip, that trip is kept and returned, with
     * its own start. Open trips never count toward any total or list.
     */
    suspend fun openTrip(sessionId: Long, startedAt: Long): Trip

    /**
     * Closes the open trip [tripId] in place with its platform, end time, amount and payment
     * method. Returns false, leaving the trip open, on invalid input (amount not positive, end not
     * after the start, unknown or deleted platform) or when [tripId] is not an open trip.
     */
    suspend fun finishOpenTrip(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean

    /**
     * Inserts a trip row. A closed trip (one with an end) must name a platform: it is rejected with
     * an [IllegalArgumentException] instead of being stored where totals and lists would disagree.
     */
    suspend fun insertTrip(trip: Trip): Long

    /** Computes the daily summary for the given session id (one-shot). */
    suspend fun summary(sessionId: Long): DailySummary

    /** Total of all trip amounts for the session, in minor units. */
    suspend fun totalMinor(sessionId: Long): Long
}
