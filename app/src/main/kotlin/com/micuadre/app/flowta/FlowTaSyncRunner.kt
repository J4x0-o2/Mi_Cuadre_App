package com.micuadre.app.flowta

import android.util.Log
import com.micuadre.app.data.local.dao.PendingFlowTaIncomeDao
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Drains the income queue through [FlowTaClient]; the WorkManager worker only calls [drain]. */
class FlowTaSyncRunner(
    private val dao: PendingFlowTaIncomeDao,
    private val client: FlowTaClient,
    private val preferences: FlowTaSyncPreferenceRepository,
    private val batchSize: Int
) {
    @Inject
    constructor(
        dao: PendingFlowTaIncomeDao,
        client: FlowTaClient,
        preferences: FlowTaSyncPreferenceRepository
    ) : this(dao, client, preferences, DEFAULT_BATCH_SIZE)

    enum class Outcome {
        /** Nothing more to do now (queue empty, disabled, or waiting for the user to reconnect). */
        DONE,

        /** A temporary failure; run again with backoff. */
        RETRY
    }

    /**
     * A row-level failure keeps its row and moves on, so one poisoned row cannot block the rest.
     * Rows that failed are not tried again in the same run; an environment failure ends it.
     */
    suspend fun drain(): Outcome {
        if (!preferences.observe().first()) return Outcome.DONE
        val failed = mutableSetOf<Long>()
        var environmentFailed = false
        while (!environmentFailed) {
            // Failed rows stay at the head of the queue, so ask for enough rows to see past them.
            val batch = dao.oldest(batchSize + failed.size).filter { it.tripId !in failed }.take(batchSize)
            if (batch.isEmpty()) break
            for (row in batch) {
                when (val result = client.recordIncome(row)) {
                    FlowTaSendResult.Delivered -> dao.delete(row.tripId)
                    is FlowTaSendResult.Rejected -> {
                        Log.w(TAG, "FlowTa rejected trip ${row.tripId}: ${result.message}; dropping it")
                        dao.delete(row.tripId)
                    }
                    FlowTaSendResult.NotLinked -> {
                        dao.markFailed(row.tripId, "NOT_LINKED")
                        return Outcome.DONE
                    }
                    is FlowTaSendResult.RowFailure -> {
                        dao.markFailed(row.tripId, result.reason)
                        failed += row.tripId
                    }
                    is FlowTaSendResult.EnvironmentFailure -> {
                        dao.markFailed(row.tripId, result.reason)
                        failed += row.tripId
                        environmentFailed = true
                        break
                    }
                }
            }
        }
        return if (failed.isEmpty()) Outcome.DONE else Outcome.RETRY
    }

    private companion object {
        const val TAG = "FlowTaSync"
        const val DEFAULT_BATCH_SIZE = 20
    }
}
