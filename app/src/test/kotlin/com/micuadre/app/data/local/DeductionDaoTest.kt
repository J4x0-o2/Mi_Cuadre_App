package com.micuadre.app.data.local

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.entity.DeductionEntity
import com.micuadre.app.data.local.entity.SessionDeductionEntity
import com.micuadre.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The deduction DAOs over an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeductionDaoTest {

    private lateinit var db: MiCuadreDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val deductions get() = db.deductionDao()
    private val amounts get() = db.sessionDeductionDao()

    private suspend fun deduction(name: String, visible: Boolean = true, usual: Long? = null) =
        deductions.insert(
            DeductionEntity(name = name, visible = visible, usualAmountMinor = usual, createdAt = T0)
        )

    private suspend fun session(startedAt: Long) =
        db.sessionDao().insert(SessionEntity(startedAt = startedAt, endedAt = startedAt + 1_000L))

    @Test
    fun `active lists non deleted deductions in creation order, hidden ones included`() = runBlocking {
        val fuel = deduction("Combustible")
        deduction("Comida", visible = false)
        val fee = deduction("Comisión")
        deductions.softDelete(fee, T0 + 1)

        assertEquals(listOf("Combustible", "Comida"), deductions.observeActive().first().map { it.name })
        assertEquals(fuel, deductions.observeActive().first().first().id)
    }

    @Test
    fun `visible lists only visible non deleted deductions`() = runBlocking {
        deduction("Combustible")
        deduction("Comida", visible = false)
        val fee = deduction("Comisión")
        deductions.softDelete(fee, T0 + 1)

        assertEquals(listOf("Combustible"), deductions.observeVisible().first().map { it.name })
    }

    @Test
    fun `all and listAll include deleted deductions`() = runBlocking {
        deduction("Combustible")
        val fee = deduction("Comisión")
        deductions.softDelete(fee, T0 + 1)

        assertEquals(listOf("Combustible", "Comisión"), deductions.observeAll().first().map { it.name })
        assertEquals(listOf("Combustible", "Comisión"), deductions.listAll().map { it.name })
    }

    @Test
    fun `soft delete keeps the row, stamps deletedAt and is applied once`() = runBlocking {
        val id = deduction("Combustible")

        assertEquals(1, deductions.softDelete(id, T0 + 5))
        assertEquals(0, deductions.softDelete(id, T0 + 9))

        assertEquals(T0 + 5, deductions.byId(id)?.deletedAt)
    }

    @Test
    fun `rename, visibility and usual amount only touch live deductions`() = runBlocking {
        val id = deduction("Combustible", usual = 2_000L)

        assertEquals(1, deductions.rename(id, "Gasolina"))
        assertEquals(1, deductions.setVisible(id, false))
        assertEquals(1, deductions.setUsualAmount(id, 3_500L))
        assertEquals(1, deductions.setUsualAmount(id, null))
        val row = deductions.byId(id)!!
        assertEquals("Gasolina", row.name)
        assertEquals(false, row.visible)
        assertNull(row.usualAmountMinor)

        deductions.softDelete(id, T0 + 1)
        assertEquals(0, deductions.rename(id, "Otro"))
        assertEquals(0, deductions.setVisible(id, true))
        assertEquals(0, deductions.setUsualAmount(id, 1L))
        assertEquals("Gasolina", deductions.byId(id)?.name)
    }

    @Test
    fun `upsert replaces the amount of the same session and deduction`() = runBlocking {
        val s = session(T0)
        val fuel = deduction("Combustible")
        val food = deduction("Comida")

        amounts.upsert(listOf(SessionDeductionEntity(sessionId = s, deductionId = fuel, amountMinor = 1_000L)))
        amounts.upsert(
            listOf(
                SessionDeductionEntity(sessionId = s, deductionId = fuel, amountMinor = 2_500L),
                SessionDeductionEntity(sessionId = s, deductionId = food, amountMinor = 800L)
            )
        )

        val rows = amounts.listBySession(s).associate { it.deductionId to it.amountMinor }
        assertEquals(mapOf(fuel to 2_500L, food to 800L), rows)
    }

    @Test
    fun `deleteBySession clears only that session`() = runBlocking {
        val s1 = session(T0)
        val s2 = session(T0 + 10_000L)
        val fuel = deduction("Combustible")
        amounts.upsert(
            listOf(
                SessionDeductionEntity(sessionId = s1, deductionId = fuel, amountMinor = 1L),
                SessionDeductionEntity(sessionId = s2, deductionId = fuel, amountMinor = 2L)
            )
        )

        amounts.deleteBySession(s1)

        assertTrue(amounts.listBySession(s1).isEmpty())
        assertEquals(listOf(2L), amounts.listBySession(s2).map { it.amountMinor })
    }

    @Test
    fun `startedBetween returns amounts of sessions that started in the half open range`() = runBlocking {
        val before = session(T0 - 1)
        val inside = session(T0)
        val after = session(T0 + 100)
        val fuel = deduction("Combustible")
        listOf(before, inside, after).forEach {
            amounts.upsert(listOf(SessionDeductionEntity(sessionId = it, deductionId = fuel, amountMinor = it)))
        }

        val rows = amounts.observeStartedBetween(T0, T0 + 100).first()

        assertEquals(listOf(inside), rows.map { it.sessionId })
    }

    @Test
    fun `amounts are removed with their session but block a hard delete of the deduction`() = runBlocking {
        val s = session(T0)
        val fuel = deduction("Combustible")
        amounts.upsert(listOf(SessionDeductionEntity(sessionId = s, deductionId = fuel, amountMinor = 1L)))

        try {
            db.openHelper.writableDatabase.execSQL("DELETE FROM deductions WHERE id = $fuel")
            fail("expected the foreign key to block the delete")
        } catch (expected: SQLiteConstraintException) {
            // history keeps a valid deduction
        }

        db.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE id = $s")
        assertTrue(amounts.listBySession(s).isEmpty())
    }

    private companion object {
        const val T0 = 1_767_254_400_000L
    }
}
