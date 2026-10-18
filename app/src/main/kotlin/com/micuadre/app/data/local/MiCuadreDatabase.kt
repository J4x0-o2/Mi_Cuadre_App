package com.micuadre.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.micuadre.app.data.local.dao.ConnectedIntervalDao
import com.micuadre.app.data.local.dao.SessionDao
import com.micuadre.app.data.local.dao.TripDao
import com.micuadre.app.data.local.entity.ConnectedIntervalEntity
import com.micuadre.app.data.local.entity.SessionEntity
import com.micuadre.app.data.local.entity.TripEntity

@Database(
    entities = [SessionEntity::class, TripEntity::class, ConnectedIntervalEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MiCuadreDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun tripDao(): TripDao
    abstract fun connectedIntervalDao(): ConnectedIntervalDao

    companion object {
        const val NAME = "micuadre.db"
    }
}
