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

    /** A temporary failure: keep the row and try again later. */
    data class Retry(val reason: String) : FlowTaSendResult

    companion object {
        /** Maps the reply Bundle of a `recordIncome` call; anything unexpected is retried. */
        fun fromReply(reply: Bundle?): FlowTaSendResult {
            if (reply == null) return Retry("NO_REPLY")
            val message = reply.getString("message")
            return when (val status = reply.getString("status")) {
                "OK", "DUPLICATE" -> Delivered
                "NOT_LINKED" -> NotLinked
                "INVALID" -> Rejected(message ?: "INVALID")
                "ERROR" -> Retry(if (message != null) "ERROR: $message" else "ERROR")
                else -> Retry("UNKNOWN_STATUS: $status")
            }
        }

        /** Missing provider, key mismatch (SecurityException) or a provider crash: retry. */
        fun fromFailure(failure: Throwable): FlowTaSendResult =
            Retry("${failure.javaClass.simpleName}: ${failure.message}")
    }
}

/** Shared contract v1: the extras of a `recordIncome` call. */
object FlowTaIncomeContract {
    const val VERSION = 1

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
