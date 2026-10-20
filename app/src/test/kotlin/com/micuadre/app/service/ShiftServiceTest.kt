package com.micuadre.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.di.BubbleModule
import com.micuadre.app.di.ClockModule
import com.micuadre.app.di.RepositoryModule
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.BubblePreferenceRepository
import com.micuadre.app.domain.repository.ShiftRepository
import com.micuadre.app.overlay.BubbleOverlay
import com.micuadre.app.overlay.OverlayPermission
import com.micuadre.app.util.Clock
import com.micuadre.app.util.FakeClock
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
@UninstallModules(RepositoryModule::class, ClockModule::class, BubbleModule::class)
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

    private val bubblePrefs = FakeBubblePreferences()
    private val bubblePermission = FakeOverlayPermission()
    private val bubbleOverlay = FakeBubbleOverlay()

    @BindValue
    @JvmField
    val bubblePreferences: BubblePreferenceRepository = bubblePrefs

    @BindValue
    @JvmField
    val overlayPermission: OverlayPermission = bubblePermission

    @BindValue
    @JvmField
    val overlay: BubbleOverlay = bubbleOverlay

    private val context: Context get() = ApplicationProvider.getApplicationContext<Application>()

    private fun intent(action: String?, extras: Bundle? = null) =
        Intent(context, ShiftService::class.java).apply {
            this.action = action
            if (extras != null) putExtras(extras)
        }

    @Before
    fun setUp() {
        hilt.inject()
    }

    private fun newService(): ServiceController<ShiftService> {
        return Robolectric.buildService(ShiftService::class.java).create()
    }

    /** Delivers [action] and lets everything settle. */
    private fun ServiceController<ShiftService>.deliver(action: String?, extras: Bundle? = null) {
        withIntent(intent(action, extras)).startCommand(0, 1)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun ServiceController<ShiftService>.deliverCancel(tripStartedAtMs: Long?) {
        val extras = tripStartedAtMs?.let {
            Bundle().apply { putLong(ShiftServiceController.EXTRA_TRIP_STARTED_AT_MS, it) }
        }
        deliver(ShiftServiceController.ACTION_CANCEL_TRIP, extras)
    }

    private fun ServiceController<ShiftService>.deliverFinish() {
        val extras = Bundle().apply {
            putLong(ShiftServiceController.EXTRA_PLATFORM_ID, 1L)
            putLong(ShiftServiceController.EXTRA_AMOUNT_MINOR, 5_000L)
            putString(ShiftServiceController.EXTRA_PAYMENT_METHOD, PaymentMethod.CASH.name)
        }
        deliver(ShiftServiceController.ACTION_FINISH_TRIP, extras)
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

    /** Like [deliverDuringRehydration] for an already built [intent]. */
    private fun ServiceController<ShiftService>.deliverDuringRehydrationWith(intent: Intent) {
        withIntent(intent).startCommand(0, 1)
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
        val tripStart = (controller.get().controller.state.value as ShiftState.OnTrip).tripStartedAtMs

        controller.deliverCancel(tripStart)

        assertEquals(ShiftState.Connected(connectedSince), controller.get().controller.state.value)
        assertEquals(listOf(100L), repository.cancelledTripSessions)
    }

    @Test
    fun `cancel trip while Connected changes nothing`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        val before = controller.get().controller.state.value

        controller.deliverCancel(NOW)

        assertEquals(before, controller.get().controller.state.value)
        assertTrue(repository.cancelledTripSessions.isEmpty())
    }

    @Test
    fun `cancel trip aimed at a different ride is ignored`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val onTrip = controller.get().controller.state.value as ShiftState.OnTrip

        controller.deliverCancel(onTrip.tripStartedAtMs - 1L)

        assertEquals(onTrip, controller.get().controller.state.value)
        assertTrue(repository.cancelledTripSessions.isEmpty())
    }

    @Test
    fun `cancel trip without a ride identity is ignored`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val onTrip = controller.get().controller.state.value

        controller.deliverCancel(null)

        assertEquals(onTrip, controller.get().controller.state.value)
        assertTrue(repository.cancelledTripSessions.isEmpty())
    }

    @Test
    fun `a finish that wins the race while the cancel is in flight is not overwritten`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        val connectedSince = (controller.get().controller.state.value as ShiftState.Connected).connectedSinceMs
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val tripStart = (controller.get().controller.state.value as ShiftState.OnTrip).tripStartedAtMs

        repository.cancelGate = CompletableDeferred()
        controller.deliverCancel(tripStart)
        fakeClock.now = NOW + MINUTE
        controller.deliverFinish()
        assertEquals(ShiftState.Connected(connectedSince), controller.get().controller.state.value)
        assertEquals(1, repository.finishCalls)

        repository.cancelGate?.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(ShiftState.Connected(connectedSince), controller.get().controller.state.value)
        assertEquals(1, repository.finishCalls)
    }

    @Test
    fun `a cancel in flight never discards a ride started after it was requested`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val first = (controller.get().controller.state.value as ShiftState.OnTrip).tripStartedAtMs

        repository.cancelGate = CompletableDeferred()
        controller.deliverCancel(first)
        fakeClock.now = NOW + MINUTE
        controller.deliverFinish()
        fakeClock.now = NOW + 2 * MINUTE
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val second = controller.get().controller.state.value
        assertTrue("expected a second ride but was $second", second is ShiftState.OnTrip)

        repository.cancelGate?.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(second, controller.get().controller.state.value)
    }

    @Test
    fun `a failing cancel keeps the ride and does not crash`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val onTrip = controller.get().controller.state.value as ShiftState.OnTrip

        // An exception nobody handles reaches the thread's handler, which kills the process on a device.
        val uncaught = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, t -> uncaught += t }
        try {
            repository.cancelFailure = IllegalStateException("disk full")
            controller.deliverCancel(onTrip.tripStartedAtMs)
        } finally {
            thread.uncaughtExceptionHandler = previous
        }

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals(onTrip, controller.get().controller.state.value)

        // The failure is not sticky: a retry goes through.
        repository.cancelFailure = null
        controller.deliverCancel(onTrip.tripStartedAtMs)
        assertTrue(controller.get().controller.state.value is ShiftState.Connected)
    }

    @Test
    fun `cancel on a fresh service while Room is in flight discards the restored ride`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.inProgress = openTrip(sessionId = 7L, startedAt = NOW - MINUTE)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydrationWith(
            intent(
                ShiftServiceController.ACTION_CANCEL_TRIP,
                Bundle().apply { putLong(ShiftServiceController.EXTRA_TRIP_STARTED_AT_MS, NOW - MINUTE) }
            )
        )

        assertEquals(listOf(7L), repository.cancelledTripSessions)
        assertEquals(ShiftState.Connected(NOW - HOUR), controller.get().controller.state.value)
    }

    @Test
    fun `cancel aimed at another ride on a fresh service is ignored after rehydration`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.inProgress = openTrip(sessionId = 7L, startedAt = NOW - MINUTE)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydrationWith(
            intent(
                ShiftServiceController.ACTION_CANCEL_TRIP,
                Bundle().apply { putLong(ShiftServiceController.EXTRA_TRIP_STARTED_AT_MS, NOW - 2 * MINUTE) }
            )
        )

        assertTrue(repository.cancelledTripSessions.isEmpty())
        assertEquals(
            ShiftState.OnTrip(connectedSinceMs = NOW - HOUR, tripStartedAtMs = NOW - MINUTE),
            controller.get().controller.state.value
        )
    }

    private fun openTrip(sessionId: Long, startedAt: Long) = Trip(
        id = 1L,
        sessionId = sessionId,
        platformId = 0L,
        platformName = "",
        startedAt = startedAt,
        endedAt = null,
        amountMinor = null,
        paymentMethod = null
    )

    @Test
    fun `the bubble appears while connected when it is enabled and the permission is granted`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = true
        val controller = newService()

        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertTrue(bubbleOverlay.visible)
        assertTrue(bubbleOverlay.lastState is ShiftState.Connected)
    }

    @Test
    fun `the bubble follows the ride state`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = true
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        controller.deliver(ShiftServiceController.ACTION_START_TRIP)

        assertTrue(bubbleOverlay.visible)
        assertTrue(bubbleOverlay.lastState is ShiftState.OnTrip)
    }

    @Test
    fun `the bubble stays hidden while the toggle is off`() {
        bubblePrefs.enabled.value = false
        bubblePermission.granted = true
        val controller = newService()

        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertEquals(false, bubbleOverlay.visible)
        assertEquals(0, bubbleOverlay.shows)
    }

    @Test
    fun `the bubble stays hidden without the overlay permission`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = false
        val controller = newService()

        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertEquals(false, bubbleOverlay.visible)
        assertEquals(0, bubbleOverlay.shows)
    }

    @Test
    fun `turning the toggle on during a shift shows the bubble and turning it off hides it`() {
        bubblePermission.granted = true
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        assertEquals(false, bubbleOverlay.visible)

        bubblePrefs.enabled.value = true
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(bubbleOverlay.visible)

        bubblePrefs.enabled.value = false
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(false, bubbleOverlay.visible)
    }

    @Test
    fun `granting the permission while a shift runs shows the bubble without a state change`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = false
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        assertEquals(false, bubbleOverlay.visible)

        bubblePermission.granted = true
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))

        assertTrue(bubbleOverlay.visible)
    }

    @Test
    fun `pausing the shift hides the bubble`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = true
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        controller.deliver(ShiftServiceController.ACTION_DISCONNECT)

        assertEquals(false, bubbleOverlay.visible)
    }

    @Test
    fun `ending the shift hides the bubble`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = true
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)

        controller.deliver(ShiftServiceController.ACTION_END_SHIFT)

        assertEquals(false, bubbleOverlay.visible)
    }

    @Test
    fun `destroying the service removes the bubble`() {
        bubblePrefs.enabled.value = true
        bubblePermission.granted = true
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        assertTrue(bubbleOverlay.visible)

        controller.destroy()

        assertEquals(false, bubbleOverlay.visible)
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
    var inProgress: Trip? = null

    override suspend fun inProgressTrip(sessionId: Long): Trip? = inProgress?.takeIf { it.sessionId == sessionId }
    override suspend fun insertTrip(trip: Trip): Long = 1L

    val cancelledTripSessions = mutableListOf<Long>()

    /** While set, [cancelInProgressTrip] suspends until it completes: models a slow delete. */
    var cancelGate: CompletableDeferred<Unit>? = null

    /** When set, [cancelInProgressTrip] throws it: models a failing delete. */
    var cancelFailure: Throwable? = null

    override suspend fun cancelInProgressTrip(sessionId: Long) {
        yield()
        cancelGate?.await()
        cancelFailure?.let { throw it }
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
    ): Boolean {
        yield()
        finishCalls++
        return true
    }

    var finishCalls = 0
}

class FakeBubblePreferences : BubblePreferenceRepository {
    val enabled = MutableStateFlow(false)
    override val bubbleEnabled: Flow<Boolean> = enabled
    override suspend fun setBubbleEnabled(enabled: Boolean) {
        this.enabled.value = enabled
    }
}

class FakeOverlayPermission(var granted: Boolean = false) : OverlayPermission {
    override fun canDrawOverlays() = granted
}

class FakeBubbleOverlay : BubbleOverlay {
    var visible = false
    var shows = 0
    var lastState: ShiftState? = null

    override fun show(state: ShiftState, intervals: List<ConnectedInterval>) {
        visible = true
        shows++
        lastState = state
    }

    override fun hide() {
        visible = false
    }
}
