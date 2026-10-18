package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.micuadre.app.data.local.entity.ConnectedIntervalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConnectedIntervalDao {

    /** Opens an interval unless the session already has an open one (single atomic statement). */
    @Query(
        "INSERT INTO connected_intervals (sessionId, startedAt, endedAt) " +
            "SELECT :sessionId, :startedAt, NULL " +
            "WHERE NOT EXISTS (SELECT 1 FROM connected_intervals " +
            "WHERE sessionId = :sessionId AND endedAt IS NULL)"
    )
    suspend fun openIfNone(sessionId: Long, startedAt: Long)

    @Query("UPDATE connected_intervals SET endedAt = :endedAt WHERE sessionId = :sessionId AND endedAt IS NULL")
    suspend fun closeOpen(sessionId: Long, endedAt: Long)

    @Query("SELECT * FROM connected_intervals WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    suspend fun listBySession(sessionId: Long): List<ConnectedIntervalEntity>

    @Query("SELECT * FROM connected_intervals WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    fun observeBySession(sessionId: Long): Flow<List<ConnectedIntervalEntity>>
}
