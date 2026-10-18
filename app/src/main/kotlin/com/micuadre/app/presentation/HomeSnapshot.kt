package com.micuadre.app.presentation

import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/** Shift state plus the trips/total that belong to the current session. */
data class HomeSnapshot(
    val shiftState: ShiftState,
    val trips: List<Trip>,
    val totalMinor: Long,
    /** True while a session is open, including a paused (Disconnected) one. */
    val hasOpenShift: Boolean = false,
    /** Connected intervals of the session (pauses are the gaps between them). */
    val connectedIntervals: List<ConnectedInterval> = emptyList()
)

/**
 * Composes the shift-state stream with the live trips of the active session.
 *
 * Each new state cancels the previous trips subscription (`flatMapLatest`),
 * so a never-completing trips flow can never stall later state updates.
 * A paused shift ([ShiftState.Disconnected] with an open session) keeps showing that
 * session's trips and total. With no open session, trips are empty and the total is zero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun homeSnapshots(
    shiftState: Flow<ShiftState>,
    activeSessionId: suspend () -> Long?,
    observeTrips: (Long) -> Flow<List<Trip>>,
    observeIntervals: (Long) -> Flow<List<ConnectedInterval>> = { flowOf(emptyList()) }
): Flow<HomeSnapshot> = shiftState.flatMapLatest { state ->
    flow {
        val sessionId = activeSessionId()
        if (sessionId == null) {
            emit(HomeSnapshot(state, emptyList(), 0L, hasOpenShift = false))
        } else {
            emitAll(
                combine(observeTrips(sessionId), observeIntervals(sessionId)) { trips, intervals ->
                    HomeSnapshot(
                        shiftState = state,
                        trips = trips,
                        totalMinor = trips.sumOf { it.amountMinor ?: 0L },
                        hasOpenShift = true,
                        connectedIntervals = intervals
                    )
                }
            )
        }
    }
}
