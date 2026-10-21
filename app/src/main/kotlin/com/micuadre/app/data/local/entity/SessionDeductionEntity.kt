package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room row for the amount a shift spent on one deduction, in minor units.
 *
 * Deleting a session cascades to its amounts; the deduction reference does not cascade, and each
 * deduction is recorded at most once per session.
 */
@Entity(
    tableName = "session_deductions",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        // No cascade: a deduction row that shifts point at can never be hard deleted.
        ForeignKey(
            entity = DeductionEntity::class,
            parentColumns = ["id"],
            childColumns = ["deductionId"]
        )
    ],
    indices = [
        Index("sessionId"),
        Index("deductionId"),
        Index("sessionId", "deductionId", unique = true)
    ]
)
data class SessionDeductionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val sessionId: Long,
    val deductionId: Long,
    val amountMinor: Long
)
