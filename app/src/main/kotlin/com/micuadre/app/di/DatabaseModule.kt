package com.micuadre.app.di

import android.content.Context
import androidx.room.Room
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.dao.SessionDao
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
        ).fallbackToDestructiveMigration().build()

    @Provides
    fun provideSessionDao(db: MiCuadreDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTripDao(db: MiCuadreDatabase): TripDao = db.tripDao()
}
