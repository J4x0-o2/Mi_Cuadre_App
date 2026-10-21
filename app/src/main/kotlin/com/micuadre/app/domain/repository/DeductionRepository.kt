package com.micuadre.app.domain.repository

import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.SessionDeductionAmount
import kotlinx.coroutines.flow.Flow

/**
 * Outcome of a deduction write. Validation failures are values, not exceptions; storage failures
 * (disk full, locked database) still surface as exceptions.
 */
sealed interface DeductionResult {
    data object Ok : DeductionResult

    /** The name is empty after trimming. */
    data object Blank : DeductionResult

    /** The trimmed name exceeds [com.micuadre.app.domain.model.DeductionName.MAX_LENGTH]. */
    data object TooLong : DeductionResult

    /** Another non-deleted deduction already has this name (case-insensitive). */
    data object Duplicate : DeductionResult

    /** The deduction (or, when saving shift amounts, the session) does not exist or was already deleted. */
    data object NotFound : DeductionResult

    /** A usual or session amount is negative. */
    data object InvalidAmount : DeductionResult
}

/**
 * Management of the user-defined deductions and of the amounts each shift spent on them.
 *
 * Session amounts live here and not on the shift repository: they only make sense next to the
 * deduction names they resolve, and the shift repository stays focused on trips and intervals.
 */
interface DeductionRepository {

    /** Streams the deductions shown in Settings: not deleted (hidden ones included), in creation order. */
    fun observeActive(): Flow<List<Deduction>>

    /** Streams the deductions asked for when ending a shift: not deleted and visible. */
    fun observeVisible(): Flow<List<Deduction>>

    /** Adds a visible deduction named [name] (trimmed) with an optional usual amount in minor units. */
    suspend fun create(name: String, usualAmountMinor: Long? = null): DeductionResult

    /** Renames the deduction; keeping its own name with a different case is allowed. */
    suspend fun rename(id: Long, name: String): DeductionResult

    /** Shows or hides the deduction in the end-of-shift sheet. */
    suspend fun setVisible(id: Long, visible: Boolean): DeductionResult

    /** Sets the usual amount, or clears it when [amountMinor] is null. */
    suspend fun setUsualAmount(id: Long, amountMinor: Long?): DeductionResult

    /** Soft deletes the deduction: past shifts keep their amounts and its name. */
    suspend fun delete(id: Long): DeductionResult

    /**
     * Replaces every amount of [sessionId] with [amounts] (deduction id to minor units). Zero
     * amounts are not stored. Nothing is written when an amount is negative, the session is unknown
     * or a deduction is unknown; deleted deductions are accepted so a stale sheet can still be saved.
     */
    suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>): DeductionResult

    /** Amounts of one shift ordered by deduction id, with names of deleted deductions included. */
    suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount>

    /** Amounts of the shifts that started in [fromMs, toMs), ordered by session then deduction id. */
    fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>>
}
