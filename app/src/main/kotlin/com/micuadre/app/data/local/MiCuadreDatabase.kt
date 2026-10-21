package com.micuadre.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.micuadre.app.data.local.dao.ConnectedIntervalDao
import com.micuadre.app.data.local.dao.DeductionDao
import com.micuadre.app.data.local.dao.PlatformDao
import com.micuadre.app.data.local.dao.SessionDao
import com.micuadre.app.data.local.dao.SessionDeductionDao
import com.micuadre.app.data.local.dao.TripDao
import com.micuadre.app.data.local.entity.ConnectedIntervalEntity
import com.micuadre.app.data.local.entity.DeductionEntity
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.data.local.entity.SessionDeductionEntity
import com.micuadre.app.data.local.entity.SessionEntity
import com.micuadre.app.data.local.entity.TripEntity

@Database(
    entities = [
        SessionEntity::class,
        TripEntity::class,
        ConnectedIntervalEntity::class,
        PlatformEntity::class,
        DeductionEntity::class,
        SessionDeductionEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MiCuadreDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun tripDao(): TripDao
    abstract fun connectedIntervalDao(): ConnectedIntervalDao
    abstract fun platformDao(): PlatformDao
    abstract fun deductionDao(): DeductionDao
    abstract fun sessionDeductionDao(): SessionDeductionDao

    companion object {
        const val NAME = "micuadre.db"
    }
}
