package com.micuadre.app.di

import com.micuadre.app.util.Clock
import com.micuadre.app.util.SystemWallClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemWallClock): Clock

    companion object {
        /** Device time zone used to decide which local day a session belongs to. */
        @Provides
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()
    }
}
