package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.data.local.entity.SessionEntity
import com.micuadre.app.domain.model.SessionDeductionAmount
import com.micuadre.app.domain.repository.DeductionResult
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real [DeductionRepositoryImpl] over an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeductionRepositoryImplTest {

    private lateinit var db: MiCuadreDatabase
    private lateinit var clock: FakeClock
    private lateinit var repository: DeductionRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
        clock = FakeClock(T0)
        repository = DeductionRepositoryImpl(db, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun names() = repository.observeActive().first().map { it.name }

    private suspend fun idOf(name: String) = repository.observeActive().first().first { it.name == name }.id

    private suspend fun session(startedAt: Long) =
        db.sessionDao().insert(SessionEntity(startedAt = startedAt, endedAt = startedAt + 1_000L))

    @Test
    fun `create trims the name, appends in creation order and stores the usual amount`() = runBlocking {
        assertEquals(DeductionResult.Ok, repository.create("  Combustible  ", 2_500L))
        clock.now += 1
        assertEquals(DeductionResult.Ok, repository.create("Comida"))

        val all = repository.observeActive().first()
        assertEquals(listOf("Combustible", "Comida"), all.map { it.name })
        assertEquals(listOf<Long?>(2_500L, null), all.map { it.usualAmountMinor })
        assertTrue(all.all { it.visible && !it.isDeleted })
        assertEquals(T0, all.first().createdAt)
    }

    @Test
    fun `create rejects blank, over-long and negative usual amounts`() = runBlocking {
        assertEquals(DeductionResult.Blank, repository.create("   "))
        assertEquals(DeductionResult.TooLong, repository.create("x".repeat(31)))
        assertEquals(DeductionResult.InvalidAmount, repository.create("Peaje", -1L))
        assertTrue(names().isEmpty())
        assertEquals(DeductionResult.Ok, repository.create("x".repeat(30), 0L))
    }

    @Test
    fun `create rejects a duplicate ignoring case and spaces, even beyond ASCII`() = runBlocking {
        repository.create("Ñandú")

        assertEquals(DeductionResult.Duplicate, repository.create(" ñandÚ "))
        assertEquals(listOf("Ñandú"), names())
    }

    @Test
    fun `rename changes the name, allows own name with other case, rejects duplicates`() = runBlocking {
        repository.create("Combustible")
        repository.create("Comida")
        val id = idOf("Combustible")

        assertEquals(DeductionResult.Ok, repository.rename(id, " Gasolina "))
        assertEquals(DeductionResult.Ok, repository.rename(id, "GASOLINA"))
        assertEquals(DeductionResult.Duplicate, repository.rename(id, "comida"))
        assertEquals(DeductionResult.Blank, repository.rename(id, " "))
        assertEquals(DeductionResult.TooLong, repository.rename(id, "x".repeat(31)))
        assertEquals(listOf("GASOLINA", "Comida"), names())
    }

    @Test
    fun `writes on an unknown or deleted deduction are not found`() = runBlocking {
        repository.create("Combustible")
        val id = idOf("Combustible")
        repository.delete(id)

        assertEquals(DeductionResult.NotFound, repository.rename(id, "Otro"))
        assertEquals(DeductionResult.NotFound, repository.rename(999L, "Otro"))
        assertEquals(DeductionResult.NotFound, repository.setVisible(id, false))
        assertEquals(DeductionResult.NotFound, repository.setUsualAmount(id, 1L))
        assertEquals(DeductionResult.NotFound, repository.delete(id))
        assertEquals(DeductionResult.NotFound, repository.delete(999L))
    }

    @Test
    fun `setVisible moves a deduction in and out of the visible list only`() = runBlocking {
        repository.create("Combustible")
        repository.create("Comida")
        val id = idOf("Combustible")

        assertEquals(DeductionResult.Ok, repository.setVisible(id, false))
        assertFalse(repository.observeActive().first().first { it.id == id }.visible)
        assertEquals(listOf("Comida"), repository.observeVisible().first().map { it.name })

        assertEquals(DeductionResult.Ok, repository.setVisible(id, true))
        assertEquals(listOf("Combustible", "Comida"), repository.observeVisible().first().map { it.name })
    }

    @Test
    fun `setUsualAmount sets, clears and rejects negative values`() = runBlocking {
        repository.create("Combustible")
        val id = idOf("Combustible")

        assertEquals(DeductionResult.Ok, repository.setUsualAmount(id, 3_000L))
        assertEquals(3_000L, repository.observeActive().first().single().usualAmountMinor)

        assertEquals(DeductionResult.InvalidAmount, repository.setUsualAmount(id, -5L))
        assertEquals(3_000L, repository.observeActive().first().single().usualAmountMinor)

        assertEquals(DeductionResult.Ok, repository.setUsualAmount(id, null))
        assertNull(repository.observeActive().first().single().usualAmountMinor)
    }

    @Test
    fun `delete hides the deduction everywhere, stamps deletedAt and frees the name`() = runBlocking {
        repository.create("Combustible")
        val id = idOf("Combustible")
        clock.now = T0 + 5

        assertEquals(DeductionResult.Ok, repository.delete(id))

        assertTrue(names().isEmpty())
        assertTrue(repository.observeVisible().first().isEmpty())
        assertEquals(T0 + 5, db.deductionDao().byId(id)?.deletedAt)
        assertEquals(DeductionResult.Ok, repository.create("combustible"))
        assertEquals(2, db.deductionDao().listAll().size)
    }

    @Test
    fun `saving amounts stores positives only and replaces the previous ones`() = runBlocking {
        repository.create("Combustible")
        repository.create("Comida")
        repository.create("Peaje")
        val fuel = idOf("Combustible")
        val food = idOf("Comida")
        val toll = idOf("Peaje")
        val s = session(T0)

        assertEquals(DeductionResult.Ok, repository.saveSessionAmounts(s, mapOf(fuel to 1_000L, food to 0L)))
        assertEquals(
            listOf(SessionDeductionAmount(s, fuel, "Combustible", 1_000L)),
            repository.sessionAmounts(s)
        )

        assertEquals(DeductionResult.Ok, repository.saveSessionAmounts(s, mapOf(toll to 300L, food to 800L)))
        assertEquals(
            listOf(
                SessionDeductionAmount(s, food, "Comida", 800L),
                SessionDeductionAmount(s, toll, "Peaje", 300L)
            ),
            repository.sessionAmounts(s)
        )

        assertEquals(DeductionResult.Ok, repository.saveSessionAmounts(s, emptyMap()))
        assertTrue(repository.sessionAmounts(s).isEmpty())
    }

    @Test
    fun `saving a negative amount or an unknown deduction writes nothing and keeps old amounts`() = runBlocking {
        repository.create("Combustible")
        val fuel = idOf("Combustible")
        val s = session(T0)
        repository.saveSessionAmounts(s, mapOf(fuel to 1_000L))

        assertEquals(DeductionResult.InvalidAmount, repository.saveSessionAmounts(s, mapOf(fuel to -1L)))
        assertEquals(DeductionResult.NotFound, repository.saveSessionAmounts(s, mapOf(fuel to 5L, 999L to 5L)))

        assertEquals(listOf(1_000L), repository.sessionAmounts(s).map { it.amountMinor })
    }

    @Test
    fun `saving amounts for a deleted deduction succeeds and keeps its name`() = runBlocking {
        repository.create("Combustible")
        val fuel = idOf("Combustible")
        val s = session(T0)
        repository.delete(fuel)

        assertEquals(DeductionResult.Ok, repository.saveSessionAmounts(s, mapOf(fuel to 700L)))

        assertEquals(
            listOf(SessionDeductionAmount(s, fuel, "Combustible", 700L)),
            repository.sessionAmounts(s)
        )
    }

    @Test
    fun `saving amounts for an unknown session is not found and writes nothing`() = runBlocking {
        repository.create("Combustible")
        val fuel = idOf("Combustible")

        assertEquals(DeductionResult.NotFound, repository.saveSessionAmounts(999L, mapOf(fuel to 5L)))
        assertEquals(DeductionResult.NotFound, repository.saveSessionAmounts(999L, emptyMap()))

        assertTrue(repository.sessionAmounts(999L).isEmpty())
    }

    @Test
    fun `history keeps deleted deductions and shows renames`() = runBlocking {
        repository.create("Combustible")
        val fuel = idOf("Combustible")
        val s = session(T0)
        repository.saveSessionAmounts(s, mapOf(fuel to 1_000L))

        repository.rename(fuel, "Gasolina")
        assertEquals(listOf("Gasolina"), repository.sessionAmounts(s).map { it.name })

        repository.delete(fuel)
        val expected = listOf(SessionDeductionAmount(s, fuel, "Gasolina", 1_000L))
        assertEquals(expected, repository.sessionAmounts(s))
        assertEquals(expected, repository.observeAmountsStartedBetween(T0, T0 + 100).first())
    }

    @Test
    fun `range read covers sessions started in the half open range, ordered`() = runBlocking {
        repository.create("Combustible")
        repository.create("Comida")
        val fuel = idOf("Combustible")
        val food = idOf("Comida")
        val before = session(T0 - 1)
        val s1 = session(T0)
        val s2 = session(T0 + 50)
        val after = session(T0 + 100)
        listOf(before, s2, s1, after).forEach {
            repository.saveSessionAmounts(it, mapOf(food to 2L, fuel to 1L))
        }

        val rows = repository.observeAmountsStartedBetween(T0, T0 + 100).first()

        assertEquals(
            listOf(
                SessionDeductionAmount(s1, fuel, "Combustible", 1L),
                SessionDeductionAmount(s1, food, "Comida", 2L),
                SessionDeductionAmount(s2, fuel, "Combustible", 1L),
                SessionDeductionAmount(s2, food, "Comida", 2L)
            ),
            rows
        )
    }

    private companion object {
        const val T0 = 1_767_254_400_000L
    }
}
