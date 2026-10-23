package com.micuadre.app.data.local

import androidx.room.testing.MigrationTestHelper
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the real migrations against the exported schemas 1 to 4. */
@RunWith(RobolectricTestRunner::class)
// The exported schemas are copied into the unit-test assets of every variant by build.gradle.kts.
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
    fun `1 to 2 backfills one interval per session and closes the one of an open session`() {
        val before = System.currentTimeMillis() / 1000 * 1000 // SQL clock has second resolution
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            // An open session (paused or running, v1 cannot tell) must not keep an open interval.
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
            // Closed at migration time, never left open.
            assertTrue(!it.isNull(2))
            val closedAt = it.getLong(2)
            assertTrue("closed at $closedAt, before=$before", closedAt in before..System.currentTimeMillis())
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

    // ---- 2 -> 3: custom platforms ----

    private fun v2Trip(
        db: SupportSQLiteDatabase,
        platform: String,
        label: String?,
        amount: Long?,
        startedAt: Long = START_1
    ) {
        val labelSql = label?.let { "'$it'" } ?: "NULL"
        val amountSql = amount?.toString() ?: "NULL"
        db.execSQL(
            "INSERT INTO trips (sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod) " +
                "VALUES (1, '$platform', $labelSql, $startedAt, ${startedAt + 60_000L}, $amountSql, 'CASH')"
        )
    }

    private fun createV2(block: (SupportSQLiteDatabase) -> Unit = {}) {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            block(this)
            close()
        }
    }

    private fun migrate3(): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3)

    private fun SupportSQLiteDatabase.platforms(): List<Triple<Long, String, Boolean>> =
        query("SELECT id, name, visible FROM platforms WHERE deletedAt IS NULL ORDER BY id").use {
            buildList { while (it.moveToNext()) add(Triple(it.getLong(0), it.getString(1), it.getInt(2) == 1)) }
        }

    private fun SupportSQLiteDatabase.tripPlatforms(): List<Pair<Long, String>> =
        query(
            "SELECT t.id, p.name FROM trips t JOIN platforms p ON p.id = t.platformId ORDER BY t.id"
        ).use { buildList { while (it.moveToNext()) add(it.getLong(0) to it.getString(1)) } }

    @Test
    fun `2 to 3 maps InDriver and Didi trips to the default platforms`() {
        createV2 {
            v2Trip(it, "INDRIVER", null, 10_000)
            v2Trip(it, "DIDI", null, 5_000)
        }

        val db = migrate3()

        assertEquals(
            listOf(Triple(1L, "InDriver", true), Triple(2L, "Didi", true)),
            db.platforms()
        )
        assertEquals(listOf(1L to "InDriver", 2L to "Didi"), db.tripPlatforms())
    }

    @Test
    fun `2 to 3 turns each distinct OTHER label into one platform ignoring case and spaces`() {
        createV2 {
            v2Trip(it, "OTHER", "Beat", 1_000)
            v2Trip(it, "OTHER", " beat ", 2_000)
            v2Trip(it, "OTHER", "Cabify", 3_000)
        }

        val db = migrate3()

        assertEquals(listOf("InDriver", "Didi", "Beat", "Cabify"), db.platforms().map { it.second })
        assertTrue(db.platforms().all { it.third })
        assertEquals(listOf("Beat", "Beat", "Cabify"), db.tripPlatforms().map { it.second })
    }

    @Test
    fun `2 to 3 sends blank or missing OTHER labels to a single Otra platform`() {
        createV2 {
            v2Trip(it, "OTHER", null, 1_000)
            v2Trip(it, "OTHER", "   ", 2_000)
            v2Trip(it, "WHATEVER", null, 3_000)
        }

        val db = migrate3()

        assertEquals(listOf("InDriver", "Didi", "Otra"), db.platforms().map { it.second })
        assertEquals(listOf("Otra", "Otra", "Otra"), db.tripPlatforms().map { it.second })
    }

    @Test
    fun `2 to 3 reuses a default platform when an OTHER label spells its name`() {
        createV2 {
            v2Trip(it, "OTHER", "didi", 1_000)
        }

        val db = migrate3()

        assertEquals(listOf("InDriver", "Didi"), db.platforms().map { it.second })
        assertEquals(listOf(1L to "Didi"), db.tripPlatforms())
    }

    @Test
    fun `2 to 3 creates the default platforms even with no trips`() {
        createV2()

        val db = migrate3()

        assertEquals(listOf("InDriver" to true, "Didi" to true), db.platforms().map { it.second to it.third })
        db.query("SELECT COUNT(*) FROM trips").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun `2 to 3 preserves every trip with its ids, times, amounts and payment`() {
        createV2 {
            v2Trip(it, "DIDI", null, 5_000, startedAt = START_1)
            v2Trip(it, "OTHER", "Uber", null, startedAt = START_1 + 100_000L)
            v2Trip(it, "INDRIVER", null, 7_500, startedAt = START_1 + 200_000L)
        }

        val db = migrate3()

        db.query("SELECT id, sessionId, startedAt, endedAt, amountMinor, paymentMethod FROM trips ORDER BY id").use {
            assertEquals(3, it.count)
            assertTrue(it.moveToNext())
            assertEquals(listOf(1L, 1L, START_1, START_1 + 60_000L, 5_000L), (0..4).map { c -> it.getLong(c) })
            assertEquals("CASH", it.getString(5))
            assertTrue(it.moveToNext())
            assertEquals(2L, it.getLong(0))
            assertTrue(it.isNull(4))
            assertTrue(it.moveToNext())
            assertEquals(3L, it.getLong(0))
            assertEquals(7_500L, it.getLong(4))
        }
        db.query("SELECT COALESCE(SUM(amountMinor), 0) FROM trips").use {
            assertTrue(it.moveToFirst())
            assertEquals(12_500L, it.getLong(0))
        }
    }

    @Test
    fun `2 to 3 leaves no foreign key violations`() {
        createV2 {
            v2Trip(it, "DIDI", null, 5_000)
            v2Trip(it, "OTHER", "Uber", 1_000)
        }

        val db = migrate3()

        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun `a platform used by trips cannot be hard deleted`() {
        createV2 { v2Trip(it, "DIDI", null, 5_000) }
        val db = migrate3()

        db.execSQL("PRAGMA foreign_keys = ON")
        try {
            db.execSQL("DELETE FROM platforms WHERE id = 2")
            fail("expected the foreign key to block the delete")
        } catch (expected: SQLiteConstraintException) {
            // trips keep a valid platform
        }
    }

    @Test
    fun `1 to 2 to 3 chain validates and maps the old trips`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            execSQL(
                "INSERT INTO trips (sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (1, 'OTHER', 'Uber', $START_1, $END_1, 5000, 'CASH')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        assertEquals(listOf("InDriver", "Didi", "Uber"), db.platforms().map { it.second })
        assertEquals(listOf(1L to "Uber"), db.tripPlatforms())
        db.query("SELECT COUNT(*) FROM connected_intervals").use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }

    // ---- 3 -> 4: custom deductions ----

    private fun createV3(block: (SupportSQLiteDatabase) -> Unit = {}) {
        helper.createDatabase(DB_NAME, 3).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            execSQL(
                "INSERT INTO platforms (id, name, visible, createdAt, deletedAt) VALUES (1, 'Didi', 1, $START_1, NULL)"
            )
            execSQL(
                "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (1, 1, $START_1, $END_1, 5000, 'CASH')"
            )
            execSQL("INSERT INTO connected_intervals (sessionId, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            block(this)
            close()
        }
    }

    private fun migrate4(): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4)

    private fun SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use {
            assertTrue(it.moveToFirst())
            it.getInt(0)
        }

    private fun SupportSQLiteDatabase.insertFuel() {
        execSQL("INSERT INTO deductions (id, name, visible, createdAt) VALUES (1, 'Combustible', 1, $START_1)")
        execSQL("INSERT INTO session_deductions (sessionId, deductionId, amountMinor) VALUES (1, 1, 1500)")
    }

    @Test
    fun `3 to 4 keeps sessions, trips, intervals and platforms`() {
        createV3()

        val db = migrate4()

        assertEquals(1, db.count("sessions"))
        assertEquals(1, db.count("connected_intervals"))
        assertEquals(listOf("Didi"), db.platforms().map { it.second })
        db.query("SELECT sessionId, platformId, amountMinor FROM trips").use {
            assertTrue(it.moveToFirst())
            assertEquals(listOf(1L, 1L, 5_000L), (0..2).map { c -> it.getLong(c) })
        }
    }

    @Test
    fun `3 to 4 creates empty deduction tables without seeding rows`() {
        createV3()

        val db = migrate4()

        assertEquals(0, db.count("deductions"))
        assertEquals(0, db.count("session_deductions"))
    }

    @Test
    fun `3 to 4 tables accept deductions and per shift amounts`() {
        createV3()
        val db = migrate4()

        db.insertFuel()

        db.query("SELECT amountMinor FROM session_deductions WHERE sessionId = 1 AND deductionId = 1").use {
            assertTrue(it.moveToFirst())
            assertEquals(1_500L, it.getLong(0))
        }
    }

    @Test
    fun `session deductions cascade when their session is deleted`() {
        createV3()
        val db = migrate4()
        db.insertFuel()

        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM sessions WHERE id = 1")

        assertEquals(0, db.count("session_deductions"))
        assertEquals(1, db.count("deductions"))
    }

    @Test
    fun `a deduction with recorded amounts cannot be hard deleted`() {
        createV3()
        val db = migrate4()
        db.insertFuel()

        db.execSQL("PRAGMA foreign_keys = ON")
        try {
            db.execSQL("DELETE FROM deductions WHERE id = 1")
            fail("expected the foreign key to block the delete")
        } catch (expected: SQLiteConstraintException) {
            // history keeps a valid deduction
        }
    }

    @Test
    fun `a deduction can be recorded only once per session`() {
        createV3()
        val db = migrate4()
        db.insertFuel()

        try {
            db.execSQL("INSERT INTO session_deductions (sessionId, deductionId, amountMinor) VALUES (1, 1, 900)")
            fail("expected the unique index to reject the duplicate")
        } catch (expected: SQLiteConstraintException) {
            // one amount per (session, deduction)
        }
    }

    @Test
    fun `1 to 4 chain validates and keeps the old trips`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            execSQL(
                "INSERT INTO trips (sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (1, 'OTHER', 'Uber', $START_1, $END_1, 5000, 'CASH')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4
        )

        assertEquals(listOf("InDriver", "Didi", "Uber"), db.platforms().map { it.second })
        assertEquals(listOf(1L to "Uber"), db.tripPlatforms())
        assertEquals(1, db.count("connected_intervals"))
        assertEquals(0, db.count("deductions"))
        assertEquals(0, db.count("session_deductions"))
    }

    // ---- 4 -> 5: open trips (nullable amount, payment and platform) ----

    private fun createV4(block: (SupportSQLiteDatabase) -> Unit = {}) {
        helper.createDatabase(DB_NAME, 4).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            execSQL(
                "INSERT INTO platforms (id, name, visible, createdAt, deletedAt) VALUES (1, 'Didi', 1, $START_1, NULL)"
            )
            execSQL(
                "INSERT INTO trips (id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (7, 1, 1, $START_1, ${START_1 + 60_000L}, 5000, 'CASH')"
            )
            execSQL(
                "INSERT INTO trips (id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (9, 1, 1, ${START_1 + 120_000L}, ${START_1 + 180_000L}, 7500, 'TRANSFER')"
            )
            block(this)
            close()
        }
    }

    private fun migrate5(): SupportSQLiteDatabase =
        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5)

    @Test
    fun `4 to 5 keeps every trip row with its id and values`() {
        createV4()

        val db = migrate5()

        db.query(
            "SELECT id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod FROM trips ORDER BY id"
        ).use {
            assertEquals(2, it.count)
            assertTrue(it.moveToNext())
            assertEquals(
                listOf(7L, 1L, 1L, START_1, START_1 + 60_000L, 5_000L),
                (0..5).map { c -> it.getLong(c) }
            )
            assertEquals("CASH", it.getString(6))
            assertTrue(it.moveToNext())
            assertEquals(9L, it.getLong(0))
            assertEquals(7_500L, it.getLong(5))
            assertEquals("TRANSFER", it.getString(6))
        }
        db.query("SELECT SUM(amountMinor) FROM trips").use {
            assertTrue(it.moveToFirst())
            assertEquals(12_500L, it.getLong(0))
        }
    }

    @Test
    fun `4 to 5 keeps the other tables and leaves no foreign key violations`() {
        createV4()

        val db = migrate5()

        assertEquals(1, db.count("sessions"))
        assertEquals(listOf("Didi"), db.platforms().map { it.second })
        db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
    }

    @Test
    fun `4 to 5 accepts an open trip with no amount, payment or platform`() {
        createV4()
        val db = migrate5()

        db.execSQL(
            "INSERT INTO trips (sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                "VALUES (1, NULL, $START_2, NULL, NULL, NULL)"
        )

        db.query("SELECT platformId, endedAt, amountMinor, paymentMethod FROM trips WHERE startedAt = $START_2").use {
            assertTrue(it.moveToFirst())
            assertTrue(it.isNull(0) && it.isNull(1) && it.isNull(2) && it.isNull(3))
        }
    }

    @Test
    fun `4 to 5 keeps the trip foreign keys and indexes`() {
        createV4()
        val db = migrate5()
        db.execSQL("PRAGMA foreign_keys = ON")

        try {
            db.execSQL("INSERT INTO trips (sessionId, platformId, startedAt) VALUES (1, 999, $START_2)")
            fail("expected the platform foreign key to reject an unknown platform")
        } catch (expected: SQLiteConstraintException) {
            // a set platform must still exist
        }
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'trips' ORDER BY name").use {
            val names = buildList { while (it.moveToNext()) add(it.getString(0)) }
            assertEquals(listOf("index_trips_platformId", "index_trips_sessionId"), names)
        }
        db.execSQL("DELETE FROM sessions WHERE id = 1")
        assertEquals(0, db.count("trips"))
    }

    @Test
    fun `4 to 5 keeps an in-progress row that already exists`() {
        createV4 {
            it.execSQL(
                "INSERT INTO trips (id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (11, 1, 1, $START_2, NULL, NULL, NULL)"
            )
        }

        val db = migrate5()

        db.query("SELECT platformId, endedAt FROM trips WHERE id = 11").use {
            assertTrue(it.moveToFirst())
            assertEquals(1L, it.getLong(0))
            assertTrue(it.isNull(1))
        }
    }

    @Test
    fun `1 to 5 chain validates and keeps the old trips`() {
        helper.createDatabase(DB_NAME, 1).apply {
            execSQL("INSERT INTO sessions (id, startedAt, endedAt) VALUES (1, $START_1, $END_1)")
            execSQL(
                "INSERT INTO trips (sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (1, 'OTHER', 'Uber', $START_1, $END_1, 5000, 'CASH')"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            DB_NAME, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5
        )

        assertEquals(listOf("InDriver", "Didi", "Uber"), db.platforms().map { it.second })
        assertEquals(listOf(1L to "Uber"), db.tripPlatforms())
        assertEquals(1, db.count("connected_intervals"))
        assertEquals(0, db.count("deductions"))
    }

    private companion object {
        const val DB_NAME = "migration-test"
        // 2026-01-01T08:00:00Z .. 10:00:00Z, and a later open session.
        const val START_1 = 1_767_254_400_000L
        const val END_1 = 1_767_261_600_000L
        const val START_2 = 1_767_340_800_000L
    }
}
