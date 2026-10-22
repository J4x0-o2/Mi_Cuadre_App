package com.micuadre.app.service

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.di.BubbleModule
import com.micuadre.app.di.ClockModule
import com.micuadre.app.di.DeductionModule
import com.micuadre.app.di.ForegroundModule
import com.micuadre.app.di.RepositoryModule
import com.micuadre.app.domain.model.Platform
import com.micuadre.app.domain.model.ConnectedInterval
import com.micuadre.app.domain.model.DailySummary
import com.micuadre.app.domain.model.Deduction
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Session
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.model.ShiftState
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.repository.BubblePreferenceRepository
import com.micuadre.app.domain.repository.DeductionRepository
import com.micuadre.app.domain.repository.DeductionResult
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
@UninstallModules(
    RepositoryModule::class, ClockModule::class, BubbleModule::class, DeductionModule::class,
    ForegroundModule::class
)
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

    private val deductionRepository = FakeDeductionRepository(repository)

    @BindValue
    @JvmField
    val deductions: DeductionRepository = deductionRepository

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

    private val foregroundStarter = RefusableForegroundStarter()

    @BindValue
    @JvmField
    val foreground: ForegroundStarter = foregroundStarter

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
        repository.trips += openTrip(sessionId = 7L, startedAt = NOW - MINUTE)

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
        repository.trips += openTrip(sessionId = 7L, startedAt = NOW - MINUTE)

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
    fun `rehydrating an open session that never got an interval opens one so connected time keeps counting`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newService()
        controller.deliver(null)

        assertTrue(controller.get().controller.state.value is ShiftState.Connected)
        assertEquals(listOf(ConnectedInterval(1L, 7L, NOW, null)), repository.intervals)
    }

    private val pausedInterval = ConnectedInterval(1L, 7L, NOW - HOUR, NOW - 30 * MINUTE)

    private fun pausedShift() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.intervals += pausedInterval
    }

    @Test
    fun `a sticky restart of a paused shift does not reconnect it`() {
        pausedShift()

        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(pausedInterval), repository.intervals)
        assertTrue(controller.tripState() is ShiftState.Disconnected)
        assertTrue(controller.get().controller.state.value is ShiftState.Disconnected)
        assertEquals(1, repository.sessions.count { it.endedAt == null })
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNull(shadowOf(controller.get()).lastForegroundNotification)
    }

    @Test
    fun `opening the app on a paused shift does not reconnect it`() {
        pausedShift()

        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_RESTORE)

        assertEquals(listOf(pausedInterval), repository.intervals)
        assertTrue(controller.tripState() is ShiftState.Disconnected)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun `connecting after a pause still resumes the paused session`() {
        pausedShift()

        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertTrue(controller.tripState() is ShiftState.Connected)
        assertEquals(0, repository.createdSessions)
        assertEquals(1, repository.intervals.count { it.endedAt == null })
    }

    @Test
    fun `a failure after the open row was stored leaves no orphan and the next start is fresh`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        val errors = controller.collectErrors()
        repository.failAfterInsert = IllegalStateException("read-back failed")

        controller.deliver(ShiftServiceController.ACTION_START_TRIP)

        assertEquals(0, repository.trips.size)
        assertTrue(controller.tripState() is ShiftState.Connected)
        assertEquals(listOf<ShiftError>(ShiftError.StartTripFailed), errors)

        repository.failAfterInsert = null
        fakeClock.now = NOW + 5 * MINUTE
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)

        assertEquals(NOW + 5 * MINUTE, repository.openTripOf(100L)!!.startedAt)
        assertEquals(
            ShiftState.OnTrip(connectedSinceMs = NOW, tripStartedAtMs = NOW + 5 * MINUTE),
            controller.tripState()
        )
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

    private fun ServiceController<ShiftService>.collectErrors(): MutableList<ShiftError> {
        val errors = mutableListOf<ShiftError>()
        CoroutineScope(Dispatchers.Main.immediate).launch {
            get().controller.errorEvents.collect { errors += it }
        }
        shadowOf(Looper.getMainLooper()).idle()
        return errors
    }

    private fun ServiceController<ShiftService>.tripState() = get()._stateForTest().value

    @Test
    fun `starting a trip persists the open row before the ride is in progress`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)

        assertEquals(listOf(100L to NOW), repository.openedTrips)
        val open = repository.openTripOf(100L)!!
        assertEquals(NOW, open.startedAt)
        assertEquals(ShiftState.OnTrip(connectedSinceMs = NOW, tripStartedAtMs = open.startedAt), controller.tripState())
    }

    @Test
    fun `a failing insert keeps the shift Connected, reports an error and leaves no ride in memory`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        val errors = controller.collectErrors()

        val uncaught = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, t -> uncaught += t }
        try {
            repository.openFailure = IllegalStateException("disk full")
            controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        } finally {
            thread.uncaughtExceptionHandler = previous
        }

        assertEquals(emptyList<Throwable>(), uncaught)
        assertTrue(controller.tripState() is ShiftState.Connected)
        assertEquals(listOf(ShiftError.StartTripFailed), errors)
        assertTrue(repository.trips.isEmpty())

        // The failure is not sticky: a retry starts the ride.
        repository.openFailure = null
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        assertTrue(controller.tripState() is ShiftState.OnTrip)
        assertEquals(1, repository.trips.size)
    }

    @Test
    fun `finishing a ride closes the open row in place without inserting another`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val openId = repository.openTripOf(100L)!!.id

        fakeClock.now = NOW + MINUTE
        controller.deliverFinish()

        assertTrue(controller.tripState() is ShiftState.Connected)
        val row = repository.trips.single()
        assertEquals(openId, row.id)
        assertEquals(listOf(openId), repository.finishedTripIds)
        assertEquals(NOW, row.startedAt)
        assertEquals(NOW + MINUTE, row.endedAt)
        assertEquals(5_000L, row.amountMinor)
        assertEquals(null, repository.openTripOf(100L))
    }

    @Test
    fun `a rejected finish keeps the ride open and reports the error`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val onTrip = controller.tripState()
        val errors = controller.collectErrors()

        repository.finishResult = false
        fakeClock.now = NOW + MINUTE
        controller.deliverFinish()

        assertEquals(onTrip, controller.tripState())
        assertEquals(listOf(ShiftError.InvalidFinishTrip), errors)
        assertEquals(NOW, repository.openTripOf(100L)!!.startedAt)

        // Nothing was lost: the retry finishes the very same row.
        repository.finishResult = true
        controller.deliverFinish()
        assertTrue(controller.tripState() is ShiftState.Connected)
        assertEquals(1, repository.trips.size)
        assertEquals(NOW + MINUTE, repository.trips.single().endedAt)
    }

    @Test
    fun `cancelling a ride deletes its open row`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        val tripStart = (controller.tripState() as ShiftState.OnTrip).tripStartedAtMs

        controller.deliverCancel(tripStart)

        assertTrue(repository.trips.isEmpty())
        assertTrue(controller.tripState() is ShiftState.Connected)
    }

    @Test
    fun `after the process dies mid-ride a new service restores the ride with its original start`() {
        val first = newService()
        first.deliver(ShiftServiceController.ACTION_CONNECT)
        first.deliver(ShiftServiceController.ACTION_START_TRIP)
        val rowBefore = repository.openTripOf(100L)!!
        // The process is killed: the service instance and everything in memory is gone.
        first.destroy()

        fakeClock.now = NOW + 10 * MINUTE
        val restarted = newService()
        restarted.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            ShiftState.OnTrip(connectedSinceMs = NOW, tripStartedAtMs = NOW),
            restarted.tripState()
        )
        val notification = shadowOf(restarted.get()).lastForegroundNotification
        assertNotNull(notification)
        assertEquals(NOW, notification!!.`when`)
        assertEquals(1, repository.openedTrips.size)

        // Finishing on the restored service closes the very same row.
        restarted.deliverFinish()
        assertTrue(restarted.tripState() is ShiftState.Connected)
        assertEquals(listOf(rowBefore.id), repository.finishedTripIds)
        assertEquals(1, repository.trips.size)
        assertEquals(NOW + 10 * MINUTE, repository.trips.single().endedAt)
    }

    @Test
    fun `cancel after a restart deletes the restored open row`() {
        val first = newService()
        first.deliver(ShiftServiceController.ACTION_CONNECT)
        first.deliver(ShiftServiceController.ACTION_START_TRIP)
        first.destroy()

        val restarted = newService()
        restarted.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()
        restarted.deliverCancel(NOW)

        assertTrue(repository.trips.isEmpty())
        assertTrue(restarted.tripState() is ShiftState.Connected)
    }

    @Test
    fun `a system restart without an intent keeps an active shift running and sticky`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.trips += openTrip(sessionId = 7L, startedAt = NOW - MINUTE)

        val controller = newService()
        val result = controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(android.app.Service.START_STICKY, result)
        assertEquals(
            ShiftState.OnTrip(connectedSinceMs = NOW - HOUR, tripStartedAtMs = NOW - MINUTE),
            controller.tripState()
        )
        assertNotNull(shadowOf(controller.get()).lastForegroundNotification)
        assertEquals(false, shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun `a system restart without an intent and without a session stops the idle service`() {
        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(controller.tripState() is ShiftState.Disconnected)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertEquals(null, shadowOf(controller.get()).lastForegroundNotification)
    }

    @Test
    fun `a system restart without an intent is not forced into the foreground`() {
        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(null, shadowOf(controller.get()).lastForegroundNotification)
    }

    private fun savedNotification() =
        shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            .getNotification(NotificationFactory.NOTIF_ID_SHIFT_SAVED)

    private fun persistedRide() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.trips += openTrip(sessionId = 7L, startedAt = NOW - MINUTE)
    }

    @Test
    fun `a sticky restart refused by the system does not crash, keeps the ride and tells the driver`() {
        persistedRide()
        foregroundStarter.refusal = IllegalStateException("ForegroundServiceStartNotAllowedException")

        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repository.trips.count { it.endedAt == null })
        assertEquals(NOW - MINUTE, repository.openTripOf(7L)!!.startedAt)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNull(shadowOf(controller.get()).lastForegroundNotification)
        val saved = savedNotification()
        assertNotNull(saved)
        assertEquals(
            context.getString(com.micuadre.app.R.string.notif_shift_saved_title),
            saved.extras.getString(android.app.Notification.EXTRA_TITLE)
        )
        assertFalse(saved.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertNotNull(saved.contentIntent)
    }

    @Test
    fun `a security refusal of the foreground start is handled the same way`() {
        persistedRide()
        foregroundStarter.refusal = SecurityException("missing foreground service type permission")

        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNotNull(savedNotification())
    }

    @Test
    fun `a refused restart posts nothing when notifications are not allowed but still stops cleanly`() {
        persistedRide()
        foregroundStarter.refusal = IllegalStateException("not allowed")
        shadowOf(context.getSystemService(android.app.NotificationManager::class.java))
            .setNotificationsEnabled(false)

        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        assertNull(savedNotification())
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertEquals(1, repository.trips.count { it.endedAt == null })
    }

    @Test
    fun `opening the app after a refused restart restores the ride`() {
        persistedRide()
        foregroundStarter.refusal = IllegalStateException("not allowed")
        val refused = newService()
        refused.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        // The app is now in the foreground: the restore request starts a service the system accepts.
        foregroundStarter.refusal = null
        val reopened = newService()
        reopened.deliver(ShiftServiceController.ACTION_RESTORE)

        assertEquals(
            ShiftState.OnTrip(connectedSinceMs = NOW - HOUR, tripStartedAtMs = NOW - MINUTE),
            reopened.tripState()
        )
        assertNotNull(shadowOf(reopened.get()).lastForegroundNotification)
        assertEquals(false, shadowOf(reopened.get()).isStoppedBySelf)
        assertNull(savedNotification())
    }

    @Test
    fun `a restore request with no persisted shift stops the idle service`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_RESTORE)

        assertTrue(controller.tripState() is ShiftState.Disconnected)
        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun `a refused foreground start on a delivered intent does not crash either`() {
        foregroundStarter.refusal = IllegalStateException("not allowed")
        val controller = newService()

        controller.deliver(ShiftServiceController.ACTION_CONNECT)

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun `ending the shift mid-ride removes the open row and keeps the finished trips`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        val closed = openTrip(sessionId = 7L, startedAt = NOW - 2 * MINUTE)
            .copy(id = 50L, platformId = 1L, endedAt = NOW - MINUTE, amountMinor = 5_000L, paymentMethod = PaymentMethod.CASH)
        repository.trips += closed
        repository.trips += openTrip(sessionId = 7L, startedAt = NOW - 30_000L)

        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_END_SHIFT)

        assertEquals(listOf(closed), repository.trips.toList())
        assertEquals(listOf(7L to NOW), repository.endedSessions)
        assertTrue(controller.tripState() is ShiftState.Disconnected)
    }

    @Test
    fun `an open row left by a failed delete does not stop the shift from ending`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        repository.trips += openTrip(sessionId = 7L, startedAt = NOW - MINUTE)
        repository.cancelFailure = IllegalStateException("disk full")

        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_END_SHIFT)

        assertEquals(listOf(7L to NOW), repository.endedSessions)
    }

    @Test
    fun `a shift paused while the open row was being inserted leaves no open row and no ride`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        repository.insertedGate = CompletableDeferred()

        // The insert has happened but has not returned yet when the driver disconnects.
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        assertEquals(1, repository.trips.count { it.endedAt == null })
        controller.deliver(ShiftServiceController.ACTION_DISCONNECT)
        repository.insertedGate!!.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, repository.trips.size)
        assertTrue(controller.tripState() !is ShiftState.OnTrip)
    }

    @Test
    fun `a duplicate start while the ride is live leaves its row and start time untouched`() {
        val controller = newService()
        controller.deliver(ShiftServiceController.ACTION_CONNECT)
        repository.insertedGate = CompletableDeferred()

        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        fakeClock.now = NOW + MINUTE
        controller.deliver(ShiftServiceController.ACTION_START_TRIP)
        repository.insertedGate!!.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, repository.trips.size)
        assertEquals(NOW, repository.trips.single().startedAt)
        assertEquals(
            ShiftState.OnTrip(connectedSinceMs = NOW, tripStartedAtMs = NOW),
            controller.tripState()
        )
    }


    @Test
    fun `a refused restart resets the published state so opening the app restores the ride`() {
        persistedRide()
        foregroundStarter.refusal = IllegalStateException("not allowed")

        val controller = newService()
        controller.get().onStartCommand(null, 0, 1)
        shadowOf(Looper.getMainLooper()).idle()

        val published = controller.get().controller
        assertEquals(ShiftState.Disconnected, published.state.value)
        assertEquals(ShiftState.Disconnected, controller.tripState())
        // The same gate MainViewModel uses on launch: a persisted shift and nothing published.
        var restores = 0
        kotlinx.coroutines.runBlocking {
            com.micuadre.app.presentation.PersistedShiftRestorer(
                state = { published.state.value },
                hasRestorableShift = { repository.activeSession() != null },
                restore = { restores++ },
                onFailure = {}
            ).restoreIfNeeded()
        }
        assertEquals(1, restores)
    }

    @Test
    fun `a failure while building the notification is not mistaken for a system refusal`() {
        val controller = newService()
        controller.get().notificationFactory = ThrowingNotificationFactory(context)

        try {
            controller.get().onStartCommand(intent(ShiftServiceController.ACTION_CONNECT), 0, 1)
            org.junit.Assert.fail("an app bug while building the notification must surface")
        } catch (expected: IllegalArgumentException) {
            assertEquals("notification bug", expected.message)
        }

        assertNull(savedNotification())
        assertFalse(shadowOf(controller.get()).isStoppedBySelf)
    }

    private fun ServiceController<ShiftService>.deliverEndShift(amounts: Map<Long, Long>) {
        deliver(ShiftServiceController.ACTION_END_SHIFT, ShiftServiceController.endShiftExtras(amounts))
    }

    @Test
    fun `end shift writes the amounts before the summary is built`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        controller.deliverEndShiftDuringRehydration(mapOf(1L to 1_500_000L, 2L to 850_000L))

        assertEquals(
            listOf(FakeDeductionRepository.Save(7L, mapOf(1L to 1_500_000L, 2L to 850_000L), summariesBefore = 0)),
            deductionRepository.saves
        )
        assertEquals(listOf(7L), repository.summarized)
        assertEquals(listOf(7L to NOW), repository.endedSessions)
    }

    @Test
    fun `the published summary carries the deductions saved just before`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        val published = mutableListOf<DailySummary>()
        val collector = CoroutineScope(Dispatchers.Unconfined)
        collector.launch { controller.get().controller.summaryEvents.collect { published += it } }
        controller.deliverEndShiftDuringRehydration(mapOf(1L to 1_500_000L, 2L to 850_000L))
        collector.cancel()

        assertEquals(
            listOf(
                SessionDeductionAmount(7L, 1L, "Deduction 1", 1_500_000L),
                SessionDeductionAmount(7L, 2L, "Deduction 2", 850_000L)
            ),
            published.single().deductions
        )
    }

    @Test
    fun `a shift ended without amounts publishes a summary without deductions`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        val published = mutableListOf<DailySummary>()
        val collector = CoroutineScope(Dispatchers.Unconfined)
        collector.launch { controller.get().controller.summaryEvents.collect { published += it } }
        controller.deliverDuringRehydration(ShiftServiceController.ACTION_END_SHIFT)
        collector.cancel()

        assertTrue(published.single().deductions.isEmpty())
    }

    @Test
    fun `a failing amounts write does not stop the shift from ending`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        deductionRepository.failure = IllegalStateException("disk full")

        val controller = newServiceWithSlowRoom()
        controller.deliverEndShiftDuringRehydration(mapOf(1L to 100L))

        assertEquals(listOf(7L to NOW), repository.endedSessions)
        assertEquals(listOf(7L), repository.summarized)
        assertTrue(controller.get().controller.state.value is ShiftState.Disconnected)
    }

    @Test
    fun `a refused amounts write does not stop the shift from ending`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)
        deductionRepository.result = DeductionResult.NotFound

        val controller = newServiceWithSlowRoom()
        controller.deliverEndShiftDuringRehydration(mapOf(99L to 100L))

        assertEquals(listOf(7L to NOW), repository.endedSessions)
        assertEquals(listOf(7L), repository.summarized)
    }

    @Test
    fun `end shift without amounts writes nothing`() {
        repository.sessions += Session(id = 7L, startedAt = NOW - HOUR, endedAt = null)

        val controller = newServiceWithSlowRoom()
        controller.deliverDuringRehydration(ShiftServiceController.ACTION_END_SHIFT)

        assertTrue(deductionRepository.saves.isEmpty())
        assertEquals(listOf(7L), repository.summarized)
    }

    @Test
    fun `end shift with no open session ignores the amounts`() {
        val controller = newService()
        controller.deliverEndShift(mapOf(1L to 100L))

        assertTrue(deductionRepository.saves.isEmpty())
        assertTrue(repository.summarized.isEmpty())
    }

    private fun ServiceController<ShiftService>.deliverEndShiftDuringRehydration(amounts: Map<Long, Long>) {
        deliverDuringRehydrationWith(
            intent(ShiftServiceController.ACTION_END_SHIFT, ShiftServiceController.endShiftExtras(amounts))
        )
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

    override fun observeConnectedIntervals(sessionId: Long): Flow<List<ConnectedInterval>> =
        flowOf(intervals.filter { it.sessionId == sessionId })

    override suspend fun closeConnectedInterval(sessionId: Long, endedAt: Long) {
        yield()
        intervals.replaceAll {
            if (it.sessionId == sessionId && it.endedAt == null) it.copy(endedAt = endedAt) else it
        }
    }

    override fun observeTrips(sessionId: Long): Flow<List<Trip>> = flowOf(emptyList())

    /** Every trip row, open (endedAt null) or closed, like the `trips` table. */
    val trips = mutableListOf<Trip>()
    private var nextTripId = 1L

    /** When set, [openTrip] throws it: models a failing insert. */
    var openFailure: Throwable? = null

    /** What [finishOpenTrip] answers: false models a rejected finish (the row stays open). */
    var finishResult = true

    /** When set, [openTrip] stores the row and then throws: a failure after the commit. */
    var failAfterInsert: Throwable? = null

    /** While set, [openTrip] suspends after storing the row, before answering: a slow insert response. */
    var insertedGate: CompletableDeferred<Unit>? = null

    val openedTrips = mutableListOf<Pair<Long, Long>>()
    val finishedTripIds = mutableListOf<Long>()

    fun openTripOf(sessionId: Long): Trip? = trips.firstOrNull { it.sessionId == sessionId && it.endedAt == null }

    override suspend fun inProgressTrip(sessionId: Long): Trip? {
        yield()
        return openTripOf(sessionId)
    }

    override suspend fun openTrip(sessionId: Long, startedAt: Long): Trip {
        yield()
        openFailure?.let { throw it }
        openTripOf(sessionId)?.let { return it }
        val trip = Trip(
            id = nextTripId++, sessionId = sessionId, platformId = 0L, platformName = "",
            startedAt = startedAt, endedAt = null, amountMinor = null, paymentMethod = null
        )
        trips += trip
        openedTrips += sessionId to startedAt
        failAfterInsert?.let { throw it }
        insertedGate?.await()
        return trip
    }

    override suspend fun finishOpenTrip(
        tripId: Long,
        platformId: Long,
        endedAt: Long,
        amountMinor: Long,
        paymentMethod: PaymentMethod
    ): Boolean {
        yield()
        finishCalls++
        if (!finishResult) return false
        val index = trips.indexOfFirst { it.id == tripId && it.endedAt == null }
        if (index < 0) return false
        trips[index] = trips[index].copy(
            platformId = platformId, endedAt = endedAt, amountMinor = amountMinor, paymentMethod = paymentMethod
        )
        finishedTripIds += tripId
        return true
    }

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
        trips.removeAll { it.sessionId == sessionId && it.endedAt == null }
    }

    override suspend fun summary(sessionId: Long): DailySummary {
        yield()
        summarized += sessionId
        return DailySummary(0L, 0, 0L, 0L)
    }

    override suspend fun totalMinor(sessionId: Long): Long = 0L

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

/** Records session amount writes and how many summaries existed when each happened. */
class FakeDeductionRepository(private val shifts: InMemoryShiftRepository) : DeductionRepository {
    data class Save(val sessionId: Long, val amounts: Map<Long, Long>, val summariesBefore: Int)

    val saves = mutableListOf<Save>()
    var failure: Throwable? = null
    var result: DeductionResult = DeductionResult.Ok
    private val stored = mutableMapOf<Long, List<SessionDeductionAmount>>()

    override suspend fun saveSessionAmounts(sessionId: Long, amounts: Map<Long, Long>): DeductionResult {
        saves += Save(sessionId, amounts, shifts.summarized.size)
        failure?.let { throw it }
        if (result == DeductionResult.Ok) {
            stored[sessionId] = amounts.filterValues { it > 0L }.map { (id, amount) ->
                SessionDeductionAmount(sessionId, id, "Deduction $id", amount)
            }
        }
        return result
    }

    override fun observeActive(): Flow<List<Deduction>> = flowOf(emptyList())
    override fun observeVisible(): Flow<List<Deduction>> = flowOf(emptyList())
    override suspend fun create(name: String, usualAmountMinor: Long?) = unused()
    override suspend fun rename(id: Long, name: String) = unused()
    override suspend fun update(id: Long, name: String, usualAmountMinor: Long?) = unused()
    override suspend fun setVisible(id: Long, visible: Boolean) = unused()
    override suspend fun setUsualAmount(id: Long, amountMinor: Long?) = unused()
    override suspend fun delete(id: Long) = unused()
    override suspend fun sessionAmounts(sessionId: Long): List<SessionDeductionAmount> =
        stored[sessionId].orEmpty()
    override fun observeAmountsStartedBetween(fromMs: Long, toMs: Long): Flow<List<SessionDeductionAmount>> =
        flowOf(emptyList())

    private fun unused(): Nothing = error("not used by the shift service")
}

/** Real foreground start, unless a [refusal] is set: models the system rejecting `startForeground`. */
class RefusableForegroundStarter : ForegroundStarter {
    var refusal: RuntimeException? = null
    private val real = PlatformForegroundStarter()

    override fun start(service: android.app.Service, notificationId: Int, notification: android.app.Notification) {
        refusal?.let { throw it }
        real.start(service, notificationId, notification)
    }
}

/** Stands in for an app bug inside notification building (not a platform refusal). */
class ThrowingNotificationFactory(context: Context) : NotificationFactory(context) {
    override fun build(state: ShiftState, totals: DayTotals): android.app.Notification =
        throw IllegalArgumentException("notification bug")
}
