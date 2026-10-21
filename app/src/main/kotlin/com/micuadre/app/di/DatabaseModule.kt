package com.micuadre.app.di

import android.content.Context
import androidx.room.Room
import com.micuadre.app.data.local.DefaultPlatforms
import com.micuadre.app.data.local.MIGRATION_1_2
import com.micuadre.app.data.local.MIGRATION_2_3
import com.micuadre.app.data.local.MIGRATION_3_4
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.dao.ConnectedIntervalDao
import com.micuadre.app.data.local.dao.DeductionDao
import com.micuadre.app.data.local.dao.PlatformDao
import com.micuadre.app.data.local.dao.SessionDao
import com.micuadre.app.data.local.dao.SessionDeductionDao
import com.micuadre.app.data.local.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MiCuadreDatabase =
        Room.databaseBuilder(
            context,
            MiCuadreDatabase::class.java,
            MiCuadreDatabase.NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(DefaultPlatforms.callback)
            .build()

    @Provides
    fun provideSessionDao(db: MiCuadreDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTripDao(db: MiCuadreDatabase): TripDao = db.tripDao()

    @Provides
    fun providePlatformDao(db: MiCuadreDatabase): PlatformDao = db.platformDao()

    @Provides
    fun provideDeductionDao(db: MiCuadreDatabase): DeductionDao = db.deductionDao()

    @Provides
    fun provideSessionDeductionDao(db: MiCuadreDatabase): SessionDeductionDao = db.sessionDeductionDao()

    @Provides
    fun provideConnectedIntervalDao(db: MiCuadreDatabase): ConnectedIntervalDao = db.connectedIntervalDao()
}
