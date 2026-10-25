package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.entity.DeductionEntity
import com.micuadre.app.data.local.entity.PlatformEntity
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.model.Trip
import com.micuadre.app.domain.usecase.ObserveMonthHistoryUseCase
import com.micuadre.app.domain.usecase.ObserveYearHistoryUseCase
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** History queries and use cases over the real repositories and an in-memory Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryUseCaseTest {

    private val zone = ZoneId.of("America/Bogota")
    private val clock = FakeClock(0L)
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
        clock.now = ms("2026-10-18", "15:30")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun shift(date: String, gross: Long, openTrip: Boolean = false): Long {
        val id = repository.createSession(ms(date, "20:00"))
        repository.recordFinishedTrip(id, didiId, ms(date, "21:00"), ms(date, "21:10"), gross, PaymentMethod.CASH)
        // A trip still in progress that already carries an amount: it must never reach the gross.
        if (openTrip) {
            repository.insertTrip(
                Trip(
                    id = 0L, sessionId = id, platformId = Trip.NO_PLATFORM_ID, platformName = "",
                    startedAt = ms(date, "22:00"), endedAt = null, amountMinor = 77_000L, paymentMethod = null
                )
            )
        }
        return id
    }

    @Test
    fun `earliest session start is null without sessions and the minimum otherwise`() = runBlocking {
        assertNull(repository.observeEarliestSessionStart().first())

        shift("2026-03-02", 1_000)
        shift("2025-11-20", 1_000)

        assertEquals(ms("2025-11-20", "20:00"), repository.observeEarliestSessionStart().first())
    }

    @Test
    fun `year use case combines trips deductions and the earliest year`() = runBlocking {
        val fuel = db.deductionDao().insert(DeductionEntity(name = "Fuel", visible = true, createdAt = 0L))
        val old = shift("2025-12-30", 40_000)
        val a = shift("2026-09-10", 20_000, openTrip = true)
        shift("2026-10-02", 8_000)
        deductions.saveSessionAmounts(a, mapOf(fuel to 5_000L))
        deductions.saveSessionAmounts(old, mapOf(fuel to 1_000L))

        val h = ObserveYearHistoryUseCase(repository, deductions, clock)(2026, zone).first()

        assertEquals(28_000L, h.totals.grossMinor)
        assertEquals(5_000L, h.totals.expensesMinor)
        // Both 2026 shifts are still live (never ended) and count, open trip or not.
        assertEquals(2, h.totals.shiftCount)
        assertEquals(listOf(YearMonth.of(2026, 10), YearMonth.of(2026, 9)), h.months.map { it.month })
        assertTrue(h.months.first().isCurrent)
        assertTrue(h.hasPreviousYear)
        assertTrue(h.isCurrentYear)
    }

    @Test
    fun `month use case returns the whole month with week totals`() = runBlocking {
        shift("2026-09-02", 10_000)
        shift("2026-09-30", 6_000)
        shift("2026-10-01", 99_000)

        val m = ObserveMonthHistoryUseCase(repository, deductions, clock)(YearMonth.of(2026, 9), zone).first()

        assertEquals(16_000L, m.totals.grossMinor)
        assertEquals(LocalDate.parse("2026-09-28"), m.weeks.last().startDate)
        assertEquals(6_000L, m.weeks.last().totals.grossMinor)
    }
}
