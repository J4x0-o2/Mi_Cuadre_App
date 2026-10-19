package com.micuadre.app.di

import com.micuadre.app.data.repository.PlatformRepositoryImpl
import com.micuadre.app.domain.repository.PlatformRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Kept apart from [RepositoryModule] so tests that replace the shift repository keep this one. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindPlatformRepository(impl: PlatformRepositoryImpl): PlatformRepository
}
