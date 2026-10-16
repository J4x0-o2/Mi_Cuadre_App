package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.service.ShiftServiceController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Exposes the shift state stream to ViewModels.
 * Delegates to [ShiftServiceController] which holds the source of truth.
 */
class ObserveShiftStateUseCase @Inject constructor(
    private val controller: ShiftServiceController
) {
    operator fun invoke(): StateFlow<ShiftState> = controller.state

    @Suppress("unused")
    fun asFlow(): Flow<ShiftState> = controller.state
}
