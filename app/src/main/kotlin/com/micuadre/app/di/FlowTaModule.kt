package com.micuadre.app.di

import com.micuadre.app.data.repository.FlowTaSyncPreferenceRepositoryImpl
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import com.micuadre.app.flowta.ContentResolverFlowTaClient
import com.micuadre.app.flowta.FlowTaAvailability
import com.micuadre.app.flowta.FlowTaClient
import com.micuadre.app.flowta.PackageManagerFlowTaAvailability
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FlowTaModule {

    @Binds
    @Singleton
    abstract fun bindSyncPreference(impl: FlowTaSyncPreferenceRepositoryImpl): FlowTaSyncPreferenceRepository

    @Binds
    abstract fun bindAvailability(impl: PackageManagerFlowTaAvailability): FlowTaAvailability

    @Binds
    abstract fun bindClient(impl: ContentResolverFlowTaClient): FlowTaClient
}
