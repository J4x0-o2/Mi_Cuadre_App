package com.micuadre.app.di

import com.micuadre.app.data.repository.BubblePreferenceRepositoryImpl
import com.micuadre.app.domain.repository.BubblePreferenceRepository
import com.micuadre.app.overlay.BubbleOverlay
import com.micuadre.app.overlay.OverlayPermission
import com.micuadre.app.overlay.WindowBubbleOverlay
import com.micuadre.app.overlay.SystemOverlayPermission
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BubbleModule {

    @Binds
    @Singleton
    abstract fun bindBubblePreferenceRepository(impl: BubblePreferenceRepositoryImpl): BubblePreferenceRepository

    @Binds
    abstract fun bindOverlayPermission(impl: SystemOverlayPermission): OverlayPermission

    @Binds
    abstract fun bindBubbleOverlay(impl: WindowBubbleOverlay): BubbleOverlay
}
