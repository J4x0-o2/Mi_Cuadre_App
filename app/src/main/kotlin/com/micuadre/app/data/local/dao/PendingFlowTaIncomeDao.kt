package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingFlowTaIncomeDao {

    /**
     * Queues the row. A trip already queued keeps its row as it is (attempts, last error and
     * creation time included), so enqueueing twice never resets the retry bookkeeping.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(row: PendingFlowTaIncomeEntity)

    /** The next rows to deliver: oldest first, at most [limit]. */
    @Query("SELECT * FROM pending_flowta_income ORDER BY createdAt ASC, tripId ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<PendingFlowTaIncomeEntity>

    @Query("DELETE FROM pending_flowta_income WHERE tripId = :tripId")
    suspend fun delete(tripId: Long)

    /** Records a failed delivery attempt: one more attempt and the error it ended with. A missing row is a no-op. */
    @Query("UPDATE pending_flowta_income SET attempts = attempts + 1, lastError = :error WHERE tripId = :tripId")
    suspend fun markFailed(tripId: Long, error: String?)

    @Query("SELECT COUNT(*) FROM pending_flowta_income")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM pending_flowta_income")
    fun observeCount(): Flow<Int>
}
