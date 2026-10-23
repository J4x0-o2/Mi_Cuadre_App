package com.micuadre.app.di

import com.micuadre.app.service.ForegroundStarter
import com.micuadre.app.service.PlatformForegroundStarter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ForegroundModule {

    @Binds
    abstract fun bindForegroundStarter(impl: PlatformForegroundStarter): ForegroundStarter
}
