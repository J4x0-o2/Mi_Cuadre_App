package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for a single trip (carrera).
 *
 * Foreign key cascades on delete: deleting a session removes its trips.
 */
@Entity(
    tableName = "trips",
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
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val platform: String,
    val platformOtherLabel: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val amountMinor: Long?,
    val paymentMethod: String?
)
