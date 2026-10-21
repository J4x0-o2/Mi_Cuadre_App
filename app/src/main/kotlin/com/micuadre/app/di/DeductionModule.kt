package com.micuadre.app.di

import com.micuadre.app.data.repository.DeductionRepositoryImpl
import com.micuadre.app.domain.repository.DeductionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Kept apart from [RepositoryModule], like [PlatformModule], so tests can replace either alone. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeductionModule {

    @Binds
    @Singleton
    abstract fun bindDeductionRepository(impl: DeductionRepositoryImpl): DeductionRepository
}
