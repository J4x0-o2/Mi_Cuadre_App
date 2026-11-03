package com.micuadre.app.flowta

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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
        // No network constraint: FlowTa is on the same device. REPLACE rather than APPEND_OR_REPLACE: an
        // append chain never ends while a run keeps asking for a retry. A new trip cancels the pending
        // or in-flight run, resets the backoff and drains promptly. Cancelling a send midway is safe:
        // FlowTa dedupes by (source_app, external_id).
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
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

    override suspend fun doWork(): Result = workResultOf(runner::drain)
}

/** Maps a drain to the work result; an unexpected exception retries rather than failing the work for good. */
internal suspend fun workResultOf(drain: suspend () -> FlowTaSyncRunner.Outcome): ListenableWorker.Result =
    try {
        when (drain()) {
            FlowTaSyncRunner.Outcome.DONE -> ListenableWorker.Result.success()
            FlowTaSyncRunner.Outcome.RETRY -> ListenableWorker.Result.retry()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("FlowTaSync", "Unexpected failure while draining the FlowTa queue", e)
        ListenableWorker.Result.retry()
    }
