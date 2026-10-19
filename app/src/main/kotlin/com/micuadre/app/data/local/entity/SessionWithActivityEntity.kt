package com.micuadre.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/** A session with its trips and connected intervals, read in one Room transaction. */
data class SessionWithActivityEntity(
    @Embedded val session: SessionEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val trips: List<TripEntity>,
    @Relation(parentColumn = "id", entityColumn = "sessionId")
    val intervals: List<ConnectedIntervalEntity>
)
