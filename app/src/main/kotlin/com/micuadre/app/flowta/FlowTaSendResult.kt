package com.micuadre.app.flowta

import android.os.Bundle
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity

/** What one `recordIncome` call means for the queued row. */
sealed interface FlowTaSendResult {
    /** FlowTa stored the income, or already had it: drop the row. */
    data object Delivered : FlowTaSendResult

    /** FlowTa has no mapping for this app: keep the row until the user reconnects. */
    data object NotLinked : FlowTaSendResult

    /** The row can never succeed (FlowTa said INVALID): drop it. */
    data class Rejected(val message: String) : FlowTaSendResult

    /** FlowTa answered but could not store this row (ERROR or unknown status): keep it, try the others. */
    data class RowFailure(val reason: String) : FlowTaSendResult

    /** FlowTa could not be reached or trusted (missing, unverified, SecurityException...): stop and retry later. */
    data class EnvironmentFailure(val reason: String) : FlowTaSendResult

    companion object {
        /** Maps the reply Bundle of a `recordIncome` call; anything unexpected is a failure to retry. */
        fun fromReply(reply: Bundle?): FlowTaSendResult {
            if (reply == null) return EnvironmentFailure("NO_REPLY")
            val message = reply.getString(FlowTaIncomeContract.KEY_MESSAGE)
            return when (val status = reply.getString(FlowTaIncomeContract.KEY_STATUS)) {
                FlowTaIncomeContract.STATUS_OK, FlowTaIncomeContract.STATUS_DUPLICATE -> Delivered
                FlowTaIncomeContract.STATUS_NOT_LINKED -> NotLinked
                FlowTaIncomeContract.STATUS_INVALID -> Rejected(message ?: "INVALID")
                FlowTaIncomeContract.STATUS_ERROR -> RowFailure(if (message != null) "ERROR: $message" else "ERROR")
                else -> RowFailure("UNKNOWN_STATUS: $status")
            }
        }

        /** Missing provider, key mismatch (SecurityException) or a provider crash: retry later. */
        fun fromFailure(failure: Throwable): FlowTaSendResult =
            EnvironmentFailure("${failure.javaClass.simpleName}: ${failure.message}")
    }
}

/** Shared contract v1: the extras of a `recordIncome` call and the keys of its reply. */
object FlowTaIncomeContract {
    const val VERSION = 1

    const val KEY_STATUS = "status"
    const val KEY_MESSAGE = "message"
    const val STATUS_OK = "OK"
    const val STATUS_DUPLICATE = "DUPLICATE"
    const val STATUS_NOT_LINKED = "NOT_LINKED"
    const val STATUS_INVALID = "INVALID"
    const val STATUS_ERROR = "ERROR"
    const val STATUS_LINKED = "LINKED"

    fun extras(row: PendingFlowTaIncomeEntity): Bundle = Bundle().apply {
        putInt("contract_version", VERSION)
        putString("external_id", row.tripId.toString())
        putString("payment_method", row.paymentMethod)
        putLong("amount_minor", row.amountMinor)
        putLong("occurred_at", row.endedAt)
        putString("note", note(row.platformName))
    }

    fun note(platformName: String): String =
        if (platformName.isBlank()) "MiCuadre" else "MiCuadre · $platformName"
}
