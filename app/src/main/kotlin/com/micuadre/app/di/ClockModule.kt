package com.micuadre.app.di

import com.micuadre.app.util.Clock
import com.micuadre.app.util.SystemWallClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ClockModule {

    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemWallClock): Clock
}
