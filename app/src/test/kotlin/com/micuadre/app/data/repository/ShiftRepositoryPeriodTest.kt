package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.data.local.entity.DeductionEntity
import com.micuadre.app.domain.model.Period
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.usecase.ObservePeriodSummaryUseCase
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.CopyOnWriteArrayList

/** Period queries of the real [ShiftRepositoryImpl] and [ObservePeriodSummaryUseCase] over in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftRepositoryPeriodTest {

    private val zone = ZoneId.of("America/Bogota")
    private lateinit var db: MiCuadreDatabase
    private lateinit var repository: ShiftRepositoryImpl
    private lateinit var deductions: DeductionRepositoryImpl
    private var didiId = 0L

    private fun ms(date: String, time: String = "00:00"): Long =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ShiftRepositoryImpl(db.sessionDao(), db.tripDao(), db.connectedIntervalDao(), db.platformDao())
        deductions = DeductionRepositoryImpl(db, FakeClock(0L))
        didiId = runBlocking {
            db.platformDao().insert(PlatformEntity(name = "Didi", visible = true, createdAt = 0L))
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun finishedTrip(session: Long, at: String, amount: Long, method: PaymentMethod) {
        repository.recordFinishedTrip(
            session, didiId, ms("2026-10-18", at), ms("2026-10-18", at) + 60_000L, amount, method
        )
    }

    @Test
    fun `sessions are selected by start time in a half-open range with their trips and intervals`() = runBlocking {
        val yesterdayNight = repository.createSession(ms("2026-10-17", "23:00"))
        val today = repository.createSession(ms("2026-10-18", "08:00"))
        val tomorrow = repository.createSession(ms("2026-10-19", "00:00"))
        repository.openConnectedInterval(today, ms("2026-10-18", "08:00"))
        finishedTrip(today, "09:00", 10_000, PaymentMethod.CASH)
        finishedTrip(yesterdayNight, "09:00", 99_000, PaymentMethod.CASH)
        finishedTrip(tomorrow, "09:00", 99_000, PaymentMethod.CASH)

        val result = repository.observeSessionActivity(ms("2026-10-18"), ms("2026-10-19")).first()

        assertEquals(listOf(today), result.map { it.session.id })
        assertEquals(listOf(10_000L), result.single().trips.map { it.amountMinor })
        assertEquals(1, result.single().intervals.size)
    }

    @Test
    fun `a night shift that ended after midnight stays with its start day`() = runBlocking {
        val night = repository.createSession(ms("2026-10-17", "20:00"))
        repository.endSession(night, ms("2026-10-18", "02:00"))

        val saturday = repository.observeSessionActivity(ms("2026-10-17"), ms("2026-10-18")).first()
        val sunday = repository.observeSessionActivity(ms("2026-10-18"), ms("2026-10-19")).first()

        assertEquals(listOf(night), saturday.map { it.session.id })
        assertEquals(emptyList<Long>(), sunday.map { it.session.id })
    }

    @Test
    fun `observation re-emits when a trip is finished`() = runBlocking {
        val session = repository.createSession(ms("2026-10-18", "08:00"))
        val seen = CopyOnWriteArrayList<Int>()
        val firstEmission = CompletableDeferred<Unit>()
        val secondEmission = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.IO) {
            repository.observeSessionActivity(ms("2026-10-18"), ms("2026-10-19")).collect {
                seen.add(it.single().trips.size)
                when (seen.size) {
                    1 -> firstEmission.complete(Unit)
                    2 -> secondEmission.complete(Unit)
                }
            }
        }
        withTimeout(5_000L) { firstEmission.await() }

        finishedTrip(session, "09:00", 5_000, PaymentMethod.TRANSFER)
        withTimeout(5_000L) { secondEmission.await() }
        collector.cancel()

        assertEquals(listOf(0, 1), seen.take(2))
    }

    @Test
    fun `use case summarises the week with an open interval counted until now`() = runBlocking {
        val clock = FakeClock(ms("2026-10-18", "15:30"))
        val monday = repository.createSession(ms("2026-10-12", "20:00"))
        repository.endSession(monday, ms("2026-10-13", "02:00"))
        finishedTrip(monday, "21:00", 20_000, PaymentMethod.CASH)
        val sunday = repository.createSession(ms("2026-10-18", "15:00"))
        repository.openConnectedInterval(sunday, ms("2026-10-18", "15:00"))
        finishedTrip(sunday, "15:10", 6_000, PaymentMethod.TRANSFER)
        val lastWeek = repository.createSession(ms("2026-10-11", "10:00"))
        finishedTrip(lastWeek, "11:00", 50_000, PaymentMethod.CASH)

        val summary = ObservePeriodSummaryUseCase(repository, deductions, clock)(Period.WEEK, zone).first()

        assertEquals(26_000L, summary.totalMinor)
        assertEquals(20_000L, summary.cashMinor)
        assertEquals(6_000L, summary.transferMinor)
        assertEquals(2, summary.sessionCount)
        assertEquals(2, summary.workedDays)
        assertEquals(1_800L, summary.connectedSeconds)
        assertEquals(7, summary.buckets.size)
    }

    @Test
    fun `use case carries the expenses of the sessions started in the week`() = runBlocking {
        val clock = FakeClock(ms("2026-10-18", "15:30"))
        val fuel = db.deductionDao().insert(DeductionEntity(name = "Fuel", visible = true, createdAt = 0L))
        val monday = repository.createSession(ms("2026-10-12", "20:00"))
        repository.endSession(monday, ms("2026-10-13", "02:00"))
        finishedTrip(monday, "21:00", 20_000, PaymentMethod.CASH)
        val lastWeek = repository.createSession(ms("2026-10-11", "10:00"))
        deductions.saveSessionAmounts(monday, mapOf(fuel to 5_000L))
        deductions.saveSessionAmounts(lastWeek, mapOf(fuel to 9_000L))

        val summary = ObservePeriodSummaryUseCase(repository, deductions, clock)(Period.WEEK, zone).first()

        assertEquals(20_000L, summary.totalMinor)
        assertEquals(5_000L, summary.costsMinor)
        assertEquals(listOf("Fuel"), summary.costs.map { it.name })
        assertEquals(5_000L, summary.buckets.first().costsMinor)
    }

    @Test
    fun `use case re-emits when the expenses change`() = runBlocking {
        val clock = FakeClock(ms("2026-10-18", "15:30"))
        val fuel = db.deductionDao().insert(DeductionEntity(name = "Fuel", visible = true, createdAt = 0L))
        val session = repository.createSession(ms("2026-10-18", "09:00"))
        val seen = CopyOnWriteArrayList<Long>()
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val collector = launch(Dispatchers.Default) {
            ObservePeriodSummaryUseCase(repository, deductions, clock)(Period.TODAY, zone).collect {
                seen += it.costsMinor
                first.complete(Unit)
                if (seen.size >= 2) second.complete(Unit)
            }
        }
        withTimeout(5_000L) { first.await() }

        deductions.saveSessionAmounts(session, mapOf(fuel to 7_000L))
        withTimeout(5_000L) { second.await() }
        collector.cancel()

        assertEquals(0L, seen.first())
        assertEquals(7_000L, seen.last())
    }
}
