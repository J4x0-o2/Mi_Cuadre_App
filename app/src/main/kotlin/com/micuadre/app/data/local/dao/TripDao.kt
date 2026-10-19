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

    @Query("SELECT * FROM trips WHERE sessionId = :sessionId ORDER BY startedAt DESC")
    fun observeBySession(sessionId: Long): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE sessionId = :sessionId ORDER BY startedAt DESC")
    suspend fun listBySession(sessionId: Long): List<TripEntity>

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM trips WHERE sessionId = :sessionId")
    suspend fun sumAmountMinor(sessionId: Long): Long

    @Query("SELECT COUNT(*) FROM trips WHERE sessionId = :sessionId")
    suspend fun countBySession(sessionId: Long): Int

    @Query("SELECT * FROM trips WHERE endedAt IS NOT NULL ORDER BY endedAt DESC LIMIT 1")
    suspend fun lastFinished(): TripEntity?

    /**
     * Returns the in-progress trip for [sessionId] (a row whose `endedAt`
     * is null), or null if there is none. Used by the service on
     * rehydration after a process restart.
     */
    @Query("SELECT * FROM trips WHERE sessionId = :sessionId AND endedAt IS NULL LIMIT 1")
    suspend fun inProgressBySession(sessionId: Long): TripEntity?
}
