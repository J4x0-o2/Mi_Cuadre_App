package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a stretch of connected time within a session. [endedAt] is null while the
 * interval is open. Foreign key cascades on delete: deleting a session removes its intervals.
 */
@Entity(
    tableName = "connected_intervals",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ConnectedIntervalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long?
)
