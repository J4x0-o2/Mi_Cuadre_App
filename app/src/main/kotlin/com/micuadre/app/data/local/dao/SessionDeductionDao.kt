package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.micuadre.app.data.local.entity.SessionDeductionEntity
import com.micuadre.app.data.local.entity.SessionDeductionRow
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDeductionDao {

    /** Inserts the amounts, replacing the existing one of the same (session, deduction) pair. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entities: List<SessionDeductionEntity>)

    @Query("DELETE FROM session_deductions WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    /** Amounts of one session with deduction names (deleted ones included), ordered by deduction. */
    @Query(
        "SELECT sd.sessionId AS sessionId, sd.deductionId AS deductionId, d.name AS name, " +
            "sd.amountMinor AS amountMinor FROM session_deductions sd " +
            "JOIN deductions d ON d.id = sd.deductionId " +
            "WHERE sd.sessionId = :sessionId ORDER BY sd.deductionId"
    )
    suspend fun namedBySession(sessionId: Long): List<SessionDeductionRow>

    /**
     * Amounts of the sessions that started in [fromMs, toMs), the same range the period queries use,
     * with deduction names. One statement, so names and amounts always come from the same snapshot.
     */
    @Query(
        "SELECT sd.sessionId AS sessionId, sd.deductionId AS deductionId, d.name AS name, " +
            "sd.amountMinor AS amountMinor FROM session_deductions sd " +
            "JOIN deductions d ON d.id = sd.deductionId JOIN sessions s ON s.id = sd.sessionId " +
            "WHERE s.startedAt >= :fromMs AND s.startedAt < :toMs ORDER BY sd.sessionId, sd.deductionId"
    )
    fun observeNamedStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionRow>>
}
