package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a platform. Rows are never hard deleted once trips reference them: deleting sets
 * [deletedAt] (soft delete) so history keeps the name.
 */
@Entity(tableName = "platforms")
data class PlatformEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val visible: Boolean,
    val createdAt: Long,
    val deletedAt: Long? = null
)
