package com.micuadre.app.flowta

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Asks for the income queue to be drained soon. */
interface FlowTaSyncScheduler {
    fun schedule()
}

class WorkManagerFlowTaSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) : FlowTaSyncScheduler {

    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<FlowTaSyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        // No network constraint: FlowTa is on the same device. APPEND_OR_REPLACE rather than KEEP: a
        // trip queued while a drain is already running (after its last read of the queue) would be
        // dropped by KEEP and wait for the next trigger; appending runs one more pass after it.
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    companion object {
        const val UNIQUE_NAME = "flowta-sync"
        private const val BACKOFF_SECONDS = 30L
    }
}

/** Thin WorkManager adapter over [FlowTaSyncRunner]. */
@HiltWorker
class FlowTaSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val runner: FlowTaSyncRunner
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (runner.drain()) {
        FlowTaSyncRunner.Outcome.DONE -> Result.success()
        FlowTaSyncRunner.Outcome.RETRY -> Result.retry()
    }
}
