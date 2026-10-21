package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.micuadre.app.data.local.entity.DeductionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeductionDao {

    @Insert
    suspend fun insert(entity: DeductionEntity): Long

    /** Deductions asked for when ending a shift: not deleted and visible, in creation order. */
    @Query("SELECT * FROM deductions WHERE deletedAt IS NULL AND visible = 1 ORDER BY id")
    fun observeVisible(): Flow<List<DeductionEntity>>

    /** Deductions managed in Settings: not deleted (hidden ones included), in creation order. */
    @Query("SELECT * FROM deductions WHERE deletedAt IS NULL ORDER BY id")
    fun observeActive(): Flow<List<DeductionEntity>>

    /** Every deduction including deleted ones, used to resolve the name shown on past shifts. */
    @Query("SELECT * FROM deductions ORDER BY id")
    fun observeAll(): Flow<List<DeductionEntity>>

    @Query("SELECT * FROM deductions ORDER BY id")
    suspend fun listAll(): List<DeductionEntity>

    /** Returns the number of rows changed: 0 when the deduction is unknown or deleted. */
    @Query("UPDATE deductions SET name = :name WHERE id = :id AND deletedAt IS NULL")
    suspend fun rename(id: Long, name: String): Int

    @Query("UPDATE deductions SET visible = :visible WHERE id = :id AND deletedAt IS NULL")
    suspend fun setVisible(id: Long, visible: Boolean): Int

    /** A null amount clears the usual amount. */
    @Query("UPDATE deductions SET usualAmountMinor = :amountMinor WHERE id = :id AND deletedAt IS NULL")
    suspend fun setUsualAmount(id: Long, amountMinor: Long?): Int

    @Query("UPDATE deductions SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, deletedAt: Long): Int

    @Query("SELECT * FROM deductions WHERE id = :id")
    suspend fun byId(id: Long): DeductionEntity?
}
