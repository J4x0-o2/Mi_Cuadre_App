package com.micuadre.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for a working session (jornada).
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startedAt: Long,
    val endedAt: Long?
)
