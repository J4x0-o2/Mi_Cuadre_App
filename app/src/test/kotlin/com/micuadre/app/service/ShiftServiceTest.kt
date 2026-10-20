package com.micuadre.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.di.ClockModule
import com.micuadre.app.di.RepositoryModule
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
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
import java.time.ZoneId
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

    private val fakeClock = FakeClock(now = NOW)

    @BindValue
    @JvmField
    val clock: Clock = fakeClock

    // ClockModule is uninstalled, so the time zone it provides must be bound here too.
    @BindValue
    @JvmField
    val zone: ZoneId = ZoneId.of("UTC")

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
    fun `connect on a new session opens one connected interval at the connect time`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertEquals(listOf(ConnectedInterval(1L, 100L, NOW, null)), repository.intervals)
    }

    @Test
    fun `disconnect closes the open interval and a reconnect opens a second one`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        fakeClock.now = NOW + HOUR
        controller.deliver(ShiftServiceController.ACTION_DISCONNECT)

        assertEquals(listOf(NOW to NOW + HOUR), repository.intervals.map { it.startedAt to it.endedAt })

        // The paused shift is resumed by a fresh service instance, 30 min later.
        fakeClock.now = NOW + HOUR + 30 * MINUTE
        val resumed = newServiceWithSlowRoom()
        resumed.deliverDuringRehydration(ShiftServiceController.ACTION_CONNECT)

        assertEquals(1, repository.createdSessions)
        assertEquals(
            listOf<Pair<Long, Long?>>(NOW to NOW + HOUR, NOW + HOUR + 30 * MINUTE to null),
            repository.intervals.map { it.startedAt to it.endedAt }
        )
    }

    @Test
    fun `cancel trip discards the ride and returns to Connected with the same connected time`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        val connectedSince = (controller.get().controller.state.value as ShiftState.Connected).connectedSinceMs
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        assertTrue(controller.get().controller.state.value is ShiftState.OnTrip)

        controller.deliver(ShiftServiceController.ACTION_CANCEL_TRIP)

        assertEquals(ShiftState.Connected(connectedSince), controller.get().controller.state.value)
        assertEquals(listOf(100L), repository.cancelledTripSessions)
    }

    @Test
    fun `cancel trip while Connected changes nothing`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        val before = controller.get().controller.state.value

        controller.deliver(ShiftServiceController.ACTION_CANCEL_TRIP)

        assertEquals(before, controller.get().controller.state.value)
        assertTrue(repository.cancelledTripSessions.isEmpty())
    }

    @Test
    fun `rehydrating a connected session keeps its interval open and adds none`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.intervals += ConnectedInterval(1L, 7L, NOW - HOUR, null)

        val controller = newService()
        controller.deliver(null)

        assertTrue(controller.get().controller.state.value is ShiftState.Connected)
        assertEquals(listOf(ConnectedInterval(1L, 7L, NOW - HOUR, null)), repository.intervals)
    }

    @Test
    fun `rehydrating an open session with no open interval opens one so connected time keeps counting`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newService()
        controller.deliver(null)

        assertTrue(controller.get().controller.state.value is ShiftState.Connected)
        assertEquals(listOf(ConnectedInterval(1L, 7L, NOW, null)), repository.intervals)
    }

    @Test
    fun `disconnect on a fresh service while Room is in flight closes the open interval`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.intervals += ConnectedInterval(1L, 7L, NOW - HOUR, null)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydration(ShiftServiceController.ACTION_DISCONNECT)

        assertEquals(
            listOf<Pair<Long, Long?>>(NOW - HOUR to NOW),
            repository.intervals.map { it.startedAt to it.endedAt }
        )
        assertTrue(controller.get().controller.state.value is ShiftState.Disconnected)
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
        const val MINUTE = 60_000L
        // 2026-01-01T08:00:00Z: a plausible wall-clock epoch.
        const val NOW = 1_767_254_400_000L
    }
}

/** In-memory [ShiftRepository] that suspends once per call, like a Room query hopping threads. */
class InMemoryShiftRepository : ShiftRepository {
    override fun observeVisiblePlatforms(): Flow<List<Platform>> = flowOf(emptyList())
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

    val intervals = mutableListOf<ConnectedInterval>()

    override suspend fun openConnectedInterval(sessionId: Long, startedAt: Long) {
        yield()
        if (intervals.none { it.sessionId == sessionId && it.endedAt == null }) {
            intervals += ConnectedInterval(intervals.size + 1L, sessionId, startedAt, endedAt = null)
        }
    }

    override suspend fun closeConnectedInterval(sessionId: Long, endedAt: Long) {
        yield()
        intervals.replaceAll {
            if (it.sessionId == sessionId && it.endedAt == null) it.copy(endedAt = endedAt) else it
        }
    }

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())
    override suspend fun inProgressTrip(sessionId: Long): Trip? = null
    override suspend fun insertTrip(trip: Trip): Long = 1L

    val cancelledTripSessions = mutableListOf<Long>()

    override suspend fun cancelInProgressTrip(sessionId: Long) {
        yield()
        cancelledTripSessions += sessionId
    }

    override suspend fun summary(sessionId: Long): DailySummary {
        yield()
        summarized += sessionId
        return DailySummary(0L, 0, 0L, 0L)
    }

    override suspend fun totalMinor(sessionId: Long): Long = 0L

    override suspend fun finishTrip(
        sessionId: Long,
        platformId: Long,
        startedAt: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean = true
}
