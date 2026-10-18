package com.micuadre.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real [MIGRATION_1_2] against the exported schemas 1 and 2. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiCuadreDatabase::class.java
    )

    @Test
    fun `1 to 2 creates connected_intervals and keeps the existing data`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            execSQL(
                "INSERT INTO trips (sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (1, 'DIDI', NULL, $START_1, $END_1, 5000, 'CASH')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM trips").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM connected_intervals").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    @Test
    fun `1 to 2 backfills one interval per session spanning the whole session`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            // An open (paused or running) session keeps an open interval.
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (2, $START_2, NULL)")
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT sessionId, startedAt, endedAt FROM connected_intervals ORDER BY sessionId").use {
            assertEquals(2, it.count)

            assertTrue(it.moveToNext())
            assertEquals(1L, it.getLong(0))
            assertEquals(START_1, it.getLong(1))
            assertEquals(END_1, it.getLong(2))

            assertTrue(it.moveToNext())
            assertEquals(2L, it.getLong(0))
            assertEquals(START_2, it.getLong(1))
            assertTrue(it.isNull(2))
        }
    }

    @Test
    fun `1 to 2 on an empty database just creates the table`() {
        helper.createDatabase(DB_NAME, 1).close()

        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM connected_intervals").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun `the interval foreign key cascades when its session is deleted`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM sessions WHERE id = 1")

        db.query("SELECT COUNT(*) FROM connected_intervals").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
        // 2026-01-01T08:00:00Z .. 10:00:00Z, and a later open session.
        const val START_1 = 1_767_254_400_000L
        const val END_1 = 1_767_261_600_000L
        const val START_2 = 1_767_340_800_000L
    }
}
