package com.micuadre.app.domain.usecase

import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.PlatformSelection
import com.micuadre.app.domain.repository.ShiftRepository
import javax.inject.Inject

/**
 * Platform to preselect in the finish-trip sheet: the one of the most recent
 * finished trip, or InDriver when there is none (or its "Otra" label is lost).
 */
class GetLastUsedPlatformUseCase @Inject constructor(
    private val repository: ShiftRepository
) {
    suspend operator fun invoke(): PlatformSelection {
        val last = repository.lastFinishedTrip() ?: return DEFAULT
        return when (last.platform) {
            Platform.OTHER -> {
                val label = last.platformOtherLabel?.trim()
                if (label.isNullOrEmpty()) DEFAULT else PlatformSelection(Platform.OTHER, label)
            }
            else -> PlatformSelection(last.platform)
        }
    }

    private companion object {
        val DEFAULT = PlatformSelection(Platform.INDRIVER)
    }
}
