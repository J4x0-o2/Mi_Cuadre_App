package com.micuadre.app.di

import com.micuadre.app.data.repository.ShiftRepositoryImpl
import com.micuadre.app.domain.repository.ShiftRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindShiftRepository(impl: ShiftRepositoryImpl): ShiftRepository
}
