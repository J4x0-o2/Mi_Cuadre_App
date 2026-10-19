package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a single trip (carrera).
 *
 * Deleting a session cascades to its trips; the platform reference does not cascade.
 */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        // No cascade: a platform row that trips point at can never be hard deleted.
        ForeignKey(
            entity = PlatformEntity::class,
            parentColumns = ["id"],
            childColumns = ["platformId"]
        )
    ],
    indices = [Index("sessionId"), Index("platformId")]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val platformId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val amountMinor: Long?,
    val paymentMethod: String?
)
