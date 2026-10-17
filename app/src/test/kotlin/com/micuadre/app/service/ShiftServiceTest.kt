package com.micuadre.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.di.ClockModule
import com.micuadre.app.di.RepositoryModule
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.util.Clock
import com.micuadre.app.util.FakeClock
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Drives the real [ShiftService] (Hilt-injected, on Robolectric) against an in-memory repository.
 * The fake repository yields before answering, like Room does, so the rehydration / connect race
 * of a freshly created service is exercised instead of being resolved synchronously.
 */
@HiltAndroidTest
@UninstallModules(RepositoryModule::class, ClockModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [34])
class ShiftServiceTest {

    @get:Rule
    val hilt = HiltAndroidRule(this)

    private val repository = InMemoryShiftRepository()

    // BindValue binds the declared type, so expose the fake as the interface the app injects.
    @BindValue
    @JvmField
    val shiftRepository: ShiftRepository = repository

    @BindValue
    @JvmField
    val clock: Clock = FakeClock(now = NOW)

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun intent(action: String?) =
        Intent(context, ShiftService::class.java).apply { this.action = action }

    @Before
    fun setUp() {
        hilt.inject()
    }

    private fun newService(): ServiceController<ShiftService> {
        return Robolectric.buildService(ShiftService::class.java).create()
    }

    /** Delivers [action] and lets everything settle. */
    private fun ServiceController<ShiftService>.deliver(action: String?) {
        withIntent(intent(action)).startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    /**
     * Delivers [action] while the service's own Room lookup (rehydration) is still in flight,
     * as happens when a fresh instance is created by the very intent being delivered. The
     * lookup is released afterwards so the service can settle.
     */
    private fun ServiceController<ShiftService>.deliverDuringRehydration(
        action: String?,
        whileInFlight: () -> Unit = {}
    ) {
        withIntent(intent(action)).startCommand(0, 1)
        whileInFlight()
        repository.gate?.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun newServiceWithSlowRoom(): ServiceController<ShiftService> {
        repository.gate = CompletableDeferred()
        return newService()
    }

    @Test
    fun `reconnect after a pause resumes the open session without creating another`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydration(ShiftServiceController.ACTION_CONNECT)

        assertEquals(0, repository.createdSessions)
        val state = controller.get().controller.state.value
        assertTrue("expected Connected but was $state", state is ShiftState.Connected)
        assertEquals(1, repository.sessions.count { it.endedAt == null })
    }

    @Test
    fun `connect with no open session creates exactly one`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertEquals(1, repository.createdSessions)
        assertTrue(controller.get().controller.state.value is ShiftState.Connected)
    }

    @Test
    fun `end shift on a fresh service instance ends the open session`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydration(ShiftServiceController.ACTION_END_SHIFT)

        assertEquals(listOf(7L to NOW), repository.endedSessions)
        assertTrue(controller.get().controller.state.value is ShiftState.Disconnected)
        assertEquals(0, repository.sessions.count { it.endedAt == null })
    }

    @Test
    fun `end shift publishes the summary of the ended session`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydration(ShiftServiceController.ACTION_END_SHIFT)

        assertEquals(listOf(7L), repository.summarized)
    }

    @Test
    fun `every delivered start intent puts the service in the foreground before anything else`() {
        val actions = listOf(
            ShiftServiceController.ACTION_CONNECT,
            ShiftServiceController.ACTION_DISCONNECT,
            ShiftServiceController.ACTION_START_TRIP,
            ShiftServiceController.ACTION_FINISH_TRIP,
            ShiftServiceController.ACTION_END_SHIFT,
            "com.micuadre.app.action.UNKNOWN"
        )
        actions.forEach { action ->
            val controller = newServiceWithSlowRoom()
            var seen: Any? = null
            controller.deliverDuringRehydration(action) {
                // Rehydration has not finished, so only the start command itself can have done this.
                seen = shadowOf(controller.get()).lastForegroundNotification
            }

            assertNotNull("no foreground notification for action $action", seen)
        }
    }

    @Test
    fun `a system restart without an intent is not forced into the foreground`() {
        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(null, shadowOf(controller.get()).lastForegroundNotification)
    }

    private companion object {
        const val HOUR = 3_600_000L
        // 2026-01-01T08:00:00Z: a plausible wall-clock epoch.
        const val NOW = 1_767_254_400_000L
    }
}

/** In-memory [ShiftRepository] that suspends once per call, like a Room query hopping threads. */
class InMemoryShiftRepository : ShiftRepository {
    val sessions = mutableListOf<Session>()
    var createdSessions = 0
    val endedSessions = mutableListOf<Pair<Long, Long>>()
    val summarized = mutableListOf<Long>()
    private var nextId = 100L

    override suspend fun createSession(startedAt: Long): Long {
        yield()
        createdSessions++
        val id = nextId++
        sessions += Session(id, startedAt, endedAt = null)
        return id
    }

    override suspend fun endSession(sessionId: Long, endedAt: Long) {
        yield()
        endedSessions += sessionId to endedAt
        val index = sessions.indexOfFirst { it.id == sessionId }
        if (index >= 0) sessions[index] = sessions[index].copy(endedAt = endedAt)
    }

    /** While set, [activeSession] suspends until it completes: models a Room query still in flight. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun activeSession(): Session? {
        yield()
        gate?.await()
        return sessions.lastOrNull { it.endedAt == null }
    }

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
    override suspend fun inProgressTrip(sessionId: Long): Trip? = null
    override suspend fun insertTrip(trip: Trip): Long = 1L

    override suspend fun summary(sessionId: Long): DailySummary {
        yield()
        summarized += sessionId
        return DailySummary(0L, 0, 0L, 0L)
    }

    override suspend fun totalMinor(sessionId: Long): Long = 0L

    override suspend fun finishTrip(
        sessionId: Long,
        platform: Platform,
        platformOtherLabel: String?,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean = true
}
