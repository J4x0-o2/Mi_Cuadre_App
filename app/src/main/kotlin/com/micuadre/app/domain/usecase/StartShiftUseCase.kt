package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Opens a new session row. Returns the generated session id.
 * The caller (service) supplies the start timestamp from a monotonic source.
 */
class StartShiftUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(startedAt: Long): Long =
        repository.createSession(startedAt = startedAt)
}
