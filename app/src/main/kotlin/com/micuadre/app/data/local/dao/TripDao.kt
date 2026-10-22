package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.micuadre.app.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Insert
    suspend fun insert(entity: TripEntity): Long

    /**
     * Inserts the trip only while [platformId] exists and is not deleted, in a single statement so
     * a concurrent delete cannot slip in between the check and the insert. Returns the new row id,
     * or -1 when nothing was inserted.
     */
    @Query(
        "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
            "SELECT :sessionId, :platformId, :startedAt, :endedAt, :amountMinor, :paymentMethod " +
            "WHERE EXISTS (SELECT 1 FROM platforms WHERE id = :platformId AND deletedAt IS NULL)"
    )
    suspend fun insertIfPlatformActive(
        sessionId: Long,
        platformId: Long,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: String
    ): Long

    /**
     * Inserts an open trip (only [startedAt]; no end, amount, payment or platform) unless the
     * session already has one, in a single statement. Returns the new row id, or -1 when the
     * session already had an open trip.
     */
    @Query(
        "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
            "SELECT :sessionId, NULL, :startedAt, NULL, NULL, NULL " +
            "WHERE NOT EXISTS (SELECT 1 FROM trips WHERE sessionId = :sessionId AND endedAt IS NULL)"
    )
    suspend fun insertOpenIfNone(sessionId: Long, startedAt: Long): Long

    /**
     * Closes the open trip [tripId] in place. Only an open trip is updated, only while
     * [platformId] exists and is not deleted, and only when [endedAt] is after its start. Returns
     * the number of rows changed (0 or 1).
     */
    @Query(
        "UPDATE trips SET platformId = :platformId, endedAt = :endedAt, amountMinor = :amountMinor, " +
            "paymentMethod = :paymentMethod " +
            "WHERE id = :tripId AND endedAt IS NULL AND startedAt < :endedAt " +
            "AND EXISTS (SELECT 1 FROM platforms WHERE id = :platformId AND deletedAt IS NULL)"
    )
    suspend fun finishOpen(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: String
    ): Int

    /** Finished trips only: an open trip is not part of any list. */
    @Query("SELECT * FROM trips WHERE sessionId = :sessionId AND endedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeBySession(sessionId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE sessionId = :sessionId AND endedAt IS NOT NULL ORDER BY startedAt DESC")
    suspend fun listBySession(sessionId: Long): List<TripEntity>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM trips WHERE sessionId = :sessionId AND endedAt IS NOT NULL")
    suspend fun sumAmountMinor(sessionId: Long): Long

    @Query("SELECT COUNT(*) FROM trips WHERE sessionId = :sessionId AND endedAt IS NOT NULL")
    suspend fun countBySession(sessionId: Long): Int

    @Query("SELECT * FROM trips WHERE endedAt IS NOT NULL AND platformId IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastFinished(): TripEntity?

    /** Discards the in-progress trip of [sessionId] (a row whose `endedAt` is null), if any. */
    @Query("DELETE FROM trips WHERE sessionId = :sessionId AND endedAt IS NULL")
    suspend fun deleteInProgressBySession(sessionId: Long)

    /**
     * Returns the in-progress trip for [sessionId] (a row whose `endedAt`
     * is null), or null if there is none. Used by the service on
     * rehydration after a process restart.
     */
    @Query("SELECT * FROM trips WHERE sessionId = :sessionId AND endedAt IS NULL LIMIT 1")
    suspend fun inProgressBySession(sessionId: Long): TripEntity?
}
