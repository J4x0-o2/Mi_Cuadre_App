package com.micuadre.app.di

import com.micuadre.app.data.repository.FlowTaSyncPreferenceRepositoryImpl
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import com.micuadre.app.flowta.ContentResolverFlowTaClient
import com.micuadre.app.flowta.FlowTaAvailability
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.flowta.DefaultFlowTaIncomeOutbox
import com.micuadre.app.flowta.FlowTaClient
import com.micuadre.app.flowta.FlowTaIncomeOutbox
import com.micuadre.app.flowta.FlowTaPlatformNames
import com.micuadre.app.flowta.FlowTaProviderVerifier
import com.micuadre.app.flowta.PackageManagerFlowTaProviderVerifier
import com.micuadre.app.flowta.FlowTaSyncScheduler
import com.micuadre.app.flowta.WorkManagerFlowTaSyncScheduler
import com.micuadre.app.flowta.PackageManagerFlowTaAvailability
import dagger.Binds
import dagger.Module
import dagger.Provides
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
    abstract fun bindProviderVerifier(impl: PackageManagerFlowTaProviderVerifier): FlowTaProviderVerifier

    @Binds
    abstract fun bindClient(impl: ContentResolverFlowTaClient): FlowTaClient

    @Binds
    abstract fun bindScheduler(impl: WorkManagerFlowTaSyncScheduler): FlowTaSyncScheduler
}

/** Split from [FlowTaModule] so service tests can swap the outbox without losing the rest. */
@Module
@InstallIn(SingletonComponent::class)
abstract class FlowTaOutboxModule {

    @Binds
    abstract fun bindOutbox(impl: DefaultFlowTaIncomeOutbox): FlowTaIncomeOutbox

    companion object {
        @Provides
        fun providePlatformNames(shifts: ShiftRepository): FlowTaPlatformNames =
            FlowTaPlatformNames(shifts::platformName)
    }
}
