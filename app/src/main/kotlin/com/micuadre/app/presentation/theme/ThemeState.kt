package com.micuadre.app.presentation.theme

import com.micuadre.app.domain.model.ThemeMode
import com.micuadre.app.domain.model.ThemeSchedule
import com.micuadre.app.util.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import java.time.ZoneId

/** The chosen [mode] plus the wall-clock instant it was last evaluated at. */
data class ThemeUiState(val mode: ThemeMode, val nowMs: Long)

/**
 * Emits whenever the effective theme may need re-evaluation: once per mode change and, in
 * [ThemeMode.SCHEDULED], again at every 06:00 / 18:00 boundary. One sleeping coroutine per
 * boundary, no polling. Cancelling (e.g. app stopped) and re-collecting re-reads the clock,
 * which is how the schedule is re-armed after a long pause. While collecting, each [clockChanges]
 * emission (time zone or wall-clock change) re-reads the clock and re-arms the schedule.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun themeStates(
    modes: Flow<ThemeMode>,
    clock: Clock,
    zone: () -> ZoneId,
    clockChanges: Flow<Unit> = emptyFlow()
): Flow<ThemeUiState> = modes.flatMapLatest { mode ->
    // Every time zone / wall-clock change restarts the sleeping coroutine from a fresh reading.
    clockChanges.onStart { emit(Unit) }.flatMapLatest {
        flow {
            while (true) {
                val now = clock.nowMs()
                emit(ThemeUiState(mode, now))
                val wait = ThemeSchedule.delayUntilNextSwitchMs(mode, now, zone()) ?: break
                delay(wait)
            }
        }
    }
}
