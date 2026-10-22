package com.micuadre.app.data.repository

import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.ShiftRepository

/**
 * Test fixture: records a finished ride the way the app does, by opening the trip and finishing it
 * in place. Returns false, leaving no open trip behind, when the finish is rejected.
 */
suspend fun ShiftRepository.recordFinishedTrip(
    sessionId: Long,
    platformId: Long,
    startedAt: Long,
    endedAt: Long,
    amountMinor: Long,
    paymentMethod: PaymentMethod
): Boolean {
    val tripId = openTrip(sessionId, startedAt)
    val finished = finishOpenTrip(tripId, platformId, endedAt, amountMinor, paymentMethod)
    if (!finished) cancelInProgressTrip(sessionId)
    return finished
}
