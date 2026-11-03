package com.micuadre.app.flowta

import android.util.Log
import com.micuadre.app.data.local.dao.PendingFlowTaIncomeDao
import com.micuadre.app.data.local.entity.PendingFlowTaIncomeEntity
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.FlowTaSyncPreferenceRepository
import com.micuadre.app.util.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Queues a just-finished trip for FlowTa. Never fails the caller. */
interface FlowTaIncomeOutbox {
    suspend fun enqueue(
        tripId: Long,
        platformId: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod,
        endedAt: Long
    )
}

/** Resolves the platform name stored with a queued income. */
fun interface FlowTaPlatformNames {
    suspend fun nameOf(platformId: Long): String?
}

class DefaultFlowTaIncomeOutbox @Inject constructor(
    private val preferences: FlowTaSyncPreferenceRepository,
    private val dao: PendingFlowTaIncomeDao,
    private val platformNames: FlowTaPlatformNames,
    private val scheduler: FlowTaSyncScheduler,
    private val clock: Clock
) : FlowTaIncomeOutbox {

    override suspend fun enqueue(
        tripId: Long,
        platformId: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod,
        endedAt: Long
    ) {
        try {
            if (!preferences.observe().first()) return
            dao.enqueue(
                PendingFlowTaIncomeEntity(
                    tripId = tripId,
                    amountMinor = amountMinor,
                    paymentMethod = paymentMethod.name,
                    endedAt = endedAt,
                    platformName = platformNames.nameOf(platformId).orEmpty(),
                    createdAt = clock.nowMs()
                )
            )
            scheduler.schedule()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The trip is already saved; losing the FlowTa copy must not break finishing it.
            Log.e("FlowTaOutbox", "Could not queue trip $tripId for FlowTa", e)
        }
    }
}
