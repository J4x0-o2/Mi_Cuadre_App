package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.micuadre.app.data.local.entity.SessionDeductionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDeductionDao {

    /** Inserts the amounts, replacing the existing one of the same (session, deduction) pair. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<SessionDeductionEntity>)

    @Query("DELETE FROM session_deductions WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("SELECT * FROM session_deductions WHERE sessionId = :sessionId ORDER BY deductionId")
    suspend fun listBySession(sessionId: Long): List<SessionDeductionEntity>

    /** Amounts of the sessions that started in [fromMs, toMs), the same range the period queries use. */
    @Query(
        "SELECT sd.* FROM session_deductions sd JOIN sessions s ON s.id = sd.sessionId " +
            "WHERE s.startedAt >= :fromMs AND s.startedAt < :toMs ORDER BY sd.sessionId, sd.deductionId"
    )
    fun observeStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionEntity>>
}
