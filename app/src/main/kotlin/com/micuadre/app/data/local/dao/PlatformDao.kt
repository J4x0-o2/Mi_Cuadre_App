package com.micuadre.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.micuadre.app.data.local.entity.PlatformEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {

    @Insert
    suspend fun insert(entity: PlatformEntity): Long

    /** Platforms offered when finishing a trip: not deleted and visible, in creation order. */
    @Query("SELECT * FROM platforms WHERE deletedAt IS NULL AND visible = 1 ORDER BY id")
    fun observeVisible(): Flow<List<PlatformEntity>>

    /** Platforms managed in Settings: not deleted (hidden ones included), in creation order. */
    @Query("SELECT * FROM platforms WHERE deletedAt IS NULL ORDER BY id")
    fun observeActive(): Flow<List<PlatformEntity>>

    /** Every platform including deleted ones, used to resolve the name shown on past trips. */
    @Query("SELECT * FROM platforms ORDER BY id")
    fun observeAll(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platforms ORDER BY id")
    suspend fun listAll(): List<PlatformEntity>

    /** Non-deleted platforms (hidden ones included), used for the duplicate-name check. */
    @Query("SELECT * FROM platforms WHERE deletedAt IS NULL ORDER BY id")
    suspend fun listActive(): List<PlatformEntity>

    /** Returns the number of rows changed: 0 when the platform is unknown or deleted. */
    @Query("UPDATE platforms SET name = :name WHERE id = :id AND deletedAt IS NULL")
    suspend fun rename(id: Long, name: String): Int

    @Query("UPDATE platforms SET visible = :visible WHERE id = :id AND deletedAt IS NULL")
    suspend fun setVisible(id: Long, visible: Boolean): Int

    @Query("UPDATE platforms SET deletedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: Long, deletedAt: Long): Int

    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun byId(id: Long): PlatformEntity?
}
