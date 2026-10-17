package com.micuadre.app.service

import com.micuadre.app.domain.model.ShiftState

/**
 * Pure decision applied by [ShiftService] once a finish-trip request has been
 * handled: advance the state machine on success, or keep the current state
 * and report an error when the request was rejected.
 */
class FinishTripReducer(private val machine: ShiftStateMachine) {

    data class Result(val state: ShiftState, val error: ShiftError?)

    fun reduce(current: ShiftState, outcome: FinishTripHandler.Outcome): Result = when (outcome) {
        FinishTripHandler.Outcome.Finished ->
            Result(machine.transition(current, ShiftStateMachine.Action.FinishTrip), error = null)
        FinishTripHandler.Outcome.Rejected ->
            Result(current, error = ShiftError.InvalidFinishTrip)
    }
}
