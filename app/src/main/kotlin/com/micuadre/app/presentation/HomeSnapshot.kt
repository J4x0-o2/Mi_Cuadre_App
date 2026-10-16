package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Shift state plus the trips/total that belong to the current session. */
data class HomeSnapshot(
    val shiftState: ShiftState,
    val trips: List<Trip>,
    val totalMinor: Long
)

/**
 * Composes the shift-state stream with the live trips of the active session.
 *
 * Each new state cancels the previous trips subscription (`flatMapLatest`),
 * so a never-completing trips flow can never stall later state updates.
 * While [ShiftState.Disconnected], or when there is no active session, trips
 * are empty and the total is zero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun homeSnapshots(
    shiftState: Flow<ShiftState>,
    activeSessionId: suspend () -> Long?,
    observeTrips: (Long) -> Flow<List<Trip>>
): Flow<HomeSnapshot> = shiftState.flatMapLatest { state ->
    if (state is ShiftState.Disconnected) {
        flow { emit(HomeSnapshot(state, emptyList(), 0L)) }
    } else {
        flow {
            val sessionId = activeSessionId()
            if (sessionId == null) {
                emit(HomeSnapshot(state, emptyList(), 0L))
            } else {
                emitAll(observeTrips(sessionId).map { trips ->
                    HomeSnapshot(state, trips, trips.sumOf { it.amountMinor ?: 0L })
                })
            }
        }
    }
}
