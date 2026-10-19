package com.micuadre.app.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.micuadre.app.data.local.MiCuadreDatabase
import com.micuadre.app.domain.model.PaymentMethod
import com.micuadre.app.domain.repository.PlatformResult
import com.micuadre.app.util.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real [PlatformRepositoryImpl] over an in-memory Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlatformRepositoryImplTest {

    private lateinit var db: MiCuadreDatabase
    private lateinit var clock: FakeClock
    private lateinit var repository: PlatformRepositoryImpl
    private lateinit var shifts: ShiftRepositoryImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            MiCuadreDatabase::class.java
        ).allowMainThreadQueries().build()
        clock = FakeClock(T0)
        repository = PlatformRepositoryImpl(db, clock)
        shifts = ShiftRepositoryImpl(db.sessionDao(), db.tripDao(), db.connectedIntervalDao(), db.platformDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun names() = repository.observeActive().first().map { it.name }

    private suspend fun idOf(name: String) = repository.observeActive().first().first { it.name == name }.id

    @Test
    fun `create trims the name and appends in creation order`() = runBlocking {
        assertEquals(PlatformResult.Ok, repository.create("  Beat  "))
        clock.now += 1
        assertEquals(PlatformResult.Ok, repository.create("Uber"))

        val platforms = repository.observeActive().first()
        assertEquals(listOf("Beat", "Uber"), platforms.map { it.name })
        assertTrue(platforms.all { it.visible && !it.isDeleted })
        assertEquals(T0, platforms.first().createdAt)
    }

    @Test
    fun `create rejects blank and over-long names`() = runBlocking {
        assertEquals(PlatformResult.Blank, repository.create("   "))
        assertEquals(PlatformResult.Blank, repository.create(""))
        assertEquals(PlatformResult.TooLong, repository.create("x".repeat(31)))
        assertEquals(PlatformResult.Ok, repository.create("x".repeat(30)))
    }

    @Test
    fun `create rejects a duplicate ignoring case and surrounding spaces`() = runBlocking {
        repository.create("Beat")

        assertEquals(PlatformResult.Duplicate, repository.create("beat"))
        assertEquals(PlatformResult.Duplicate, repository.create(" BEAT "))
        assertEquals(listOf("Beat"), names())
    }

    @Test
    fun `duplicate check is case insensitive beyond ASCII`() = runBlocking {
        repository.create("Ñandú")

        assertEquals(PlatformResult.Duplicate, repository.create("ñandÚ"))
    }

    @Test
    fun `rename changes the name and keeps the id`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")

        assertEquals(PlatformResult.Ok, repository.rename(id, " Cabify "))

        val platform = repository.observeActive().first().single()
        assertEquals(id, platform.id)
        assertEquals("Cabify", platform.name)
    }

    @Test
    fun `rename to the own name with different case is allowed`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")

        assertEquals(PlatformResult.Ok, repository.rename(id, "BEAT"))
        assertEquals(listOf("BEAT"), names())
    }

    @Test
    fun `rename to another platform name is rejected`() = runBlocking {
        repository.create("Beat")
        repository.create("Uber")

        assertEquals(PlatformResult.Duplicate, repository.rename(idOf("Uber"), "beat"))
        assertEquals(listOf("Beat", "Uber"), names())
    }

    @Test
    fun `rename rejects blank and over-long names`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")

        assertEquals(PlatformResult.Blank, repository.rename(id, " "))
        assertEquals(PlatformResult.TooLong, repository.rename(id, "x".repeat(31)))
        assertEquals(listOf("Beat"), names())
    }

    @Test
    fun `rename of an unknown or deleted platform is not found`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")
        repository.delete(id)

        assertEquals(PlatformResult.NotFound, repository.rename(id, "Other"))
        assertEquals(PlatformResult.NotFound, repository.rename(999L, "Other"))
    }

    @Test
    fun `setVisible hides and shows a platform in the finish list only`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")

        assertEquals(PlatformResult.Ok, repository.setVisible(id, false))
        assertFalse(repository.observeActive().first().single().visible)
        assertTrue(shifts.observeVisiblePlatforms().first().isEmpty())

        assertEquals(PlatformResult.Ok, repository.setVisible(id, true))
        assertEquals(listOf("Beat"), shifts.observeVisiblePlatforms().first().map { it.name })
    }

    @Test
    fun `setVisible on an unknown or deleted platform is not found`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")
        repository.delete(id)

        assertEquals(PlatformResult.NotFound, repository.setVisible(id, false))
        assertEquals(PlatformResult.NotFound, repository.setVisible(999L, true))
    }

    @Test
    fun `delete removes the platform from the active and visible lists`() = runBlocking {
        repository.create("Beat")
        repository.create("Uber")
        clock.now = T0 + 5

        assertEquals(PlatformResult.Ok, repository.delete(idOf("Beat")))

        assertEquals(listOf("Uber"), names())
        assertEquals(listOf("Uber"), shifts.observeVisiblePlatforms().first().map { it.name })
        assertEquals(T0 + 5, db.platformDao().listAll().first { it.name == "Beat" }.deletedAt)
    }

    @Test
    fun `delete of an unknown or already deleted platform is not found`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")

        assertEquals(PlatformResult.Ok, repository.delete(id))
        assertEquals(PlatformResult.NotFound, repository.delete(id))
        assertEquals(PlatformResult.NotFound, repository.delete(999L))
    }

    @Test
    fun `a deleted name can be reused by a new platform`() = runBlocking {
        repository.create("Beat")
        repository.delete(idOf("Beat"))

        assertEquals(PlatformResult.Ok, repository.create("beat"))
        assertEquals(listOf("beat"), names())
        assertEquals(2, db.platformDao().listAll().size)
    }

    @Test
    fun `delete keeps past trips with their platform name and rejects new ones`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")
        val session = shifts.createSession(T0)
        assertTrue(shifts.finishTrip(session, id, T0, T0 + MINUTE, 1_500L, PaymentMethod.CASH))

        repository.delete(id)

        val trips = shifts.observeTrips(session).first()
        assertEquals(listOf("Beat"), trips.map { it.platformName })
        assertEquals(1_500L, shifts.totalMinor(session))
        assertFalse(shifts.finishTrip(session, id, T0 + 2 * MINUTE, T0 + 3 * MINUTE, 900L, PaymentMethod.CASH))
        assertEquals(1, shifts.observeTrips(session).first().size)
    }

    @Test
    fun `a renamed platform shows the new name on past trips`() = runBlocking {
        repository.create("Beat")
        val id = idOf("Beat")
        val session = shifts.createSession(T0)
        shifts.finishTrip(session, id, T0, T0 + MINUTE, 1_500L, PaymentMethod.CASH)

        repository.rename(id, "Cabify")

        assertEquals(listOf("Cabify"), shifts.observeTrips(session).first().map { it.platformName })
    }

    private companion object {
        const val T0 = 1_700_000_000_000L
        const val MINUTE = 60_000L
    }
}
