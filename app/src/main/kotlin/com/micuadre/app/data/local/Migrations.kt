package com.micuadre.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Locale

/**
 * 1 -> 2: adds `connected_intervals` (real connected time per session, pauses excluded) and
 * backfills one interval per existing session spanning the whole session, so old shifts keep
 * the "Conectado" value they used to show.
 *
 * v1 cannot tell whether an open session is connected or paused, so the backfilled interval of
 * an open session is closed at migration time (never earlier than the session start) instead of
 * being left open. The service reopens one when it restores a connected shift (see rehydration).
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `connected_intervals` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_connected_intervals_sessionId` " +
                "ON `connected_intervals` (`sessionId`)"
        )
        db.execSQL(
            "INSERT INTO connected_intervals (sessionId, startedAt, endedAt) " +
                "SELECT id, startedAt, " +
                "COALESCE(endedAt, MAX(startedAt, CAST(strftime('%s','now') AS INTEGER) * 1000)) " +
                "FROM sessions"
        )
    }
}

/**
 * 2 -> 3: platforms become rows. Creates `platforms`, always seeds the defaults (InDriver id 1,
 * Didi id 2, visible, even if unused) and rebuilds `trips` so `platform` + `platformOtherLabel`
 * are replaced by `platformId` (foreign key, no cascade).
 *
 * Mapping: INDRIVER -> InDriver, DIDI -> Didi; every other trip (the old "Otra") goes to the
 * platform named by its trimmed label, grouped case-insensitively and keeping the casing of the
 * first trip (lowest id) that used it; a blank/missing label goes to "Otra". A label that spells
 * an existing platform name reuses that platform. Trip ids and all other columns are preserved.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `platforms` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`visible` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER)"
        )
        val now = System.currentTimeMillis()
        DefaultPlatforms.insertInto(db, now)
        val inDriverId = 1L
        val didiId = 2L
        val idsByName = linkedMapOf<String, Long>().apply {
            DefaultPlatforms.NAMES.forEachIndexed { index, name -> put(key(name), index + 1L) }
        }

        val oldTrips = mutableListOf<OldTrip>()
        db.query(
            "SELECT id, sessionId, platform, platformOtherLabel, startedAt, endedAt, amountMinor, paymentMethod " +
                "FROM trips ORDER BY id"
        ).use { c ->
            while (c.moveToNext()) {
                oldTrips += OldTrip(
                    id = c.getLong(0),
                    sessionId = c.getLong(1),
                    platform = c.getString(2),
                    label = if (c.isNull(3)) null else c.getString(3),
                    startedAt = c.getLong(4),
                    endedAt = if (c.isNull(5)) null else c.getLong(5),
                    amountMinor = if (c.isNull(6)) null else c.getLong(6),
                    paymentMethod = if (c.isNull(7)) null else c.getString(7)
                )
            }
        }

        fun createPlatform(name: String): Long {
            db.execSQL(
                "INSERT INTO platforms (name, visible, createdAt, deletedAt) VALUES (?, 1, ?, NULL)",
                arrayOf<Any?>(name, now)
            )
            return db.query("SELECT last_insert_rowid()").use { c ->
                c.moveToFirst()
                c.getLong(0)
            }
        }

        fun platformIdFor(trip: OldTrip): Long = when (trip.platform) {
            "INDRIVER" -> inDriverId
            "DIDI" -> didiId
            else -> {
                val name = trip.label?.trim().orEmpty().ifEmpty { "Otra" }
                idsByName.getOrPut(key(name)) { createPlatform(name) }
            }
        }

        val platformIds = oldTrips.associate { it.id to platformIdFor(it) }

        db.execSQL(
            "CREATE TABLE `trips_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`platformId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, " +
                "`amountMinor` INTEGER, " +
                "`paymentMethod` TEXT, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        oldTrips.forEach { t ->
            db.execSQL(
                "INSERT INTO trips_new (id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    t.id, t.sessionId, platformIds.getValue(t.id), t.startedAt, t.endedAt, t.amountMinor, t.paymentMethod
                )
            )
        }
        db.execSQL("DROP TABLE `trips`")
        db.execSQL("ALTER TABLE `trips_new` RENAME TO `trips`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_sessionId` ON `trips` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_platformId` ON `trips` (`platformId`)")
    }
}

/** A v2 trip row as read by [MIGRATION_2_3]; [platform] and [label] are the removed columns. */
private class OldTrip(
    val id: Long,
    val sessionId: Long,
    val platform: String,
    val label: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val amountMinor: Long?,
    val paymentMethod: String?
)

/** Case-insensitive identity of a platform name. */
private fun key(name: String) = name.lowercase(Locale.ROOT)

/**
 * 3 -> 4: custom deductions. Creates `deductions` and `session_deductions` (the amount a shift
 * spent on each deduction, unique per session and deduction). Nothing is rebuilt or seeded.
 */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deductions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`visible` INTEGER NOT NULL, " +
                "`usualAmountMinor` INTEGER, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`deletedAt` INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_deductions` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`deductionId` INTEGER NOT NULL, " +
                "`amountMinor` INTEGER NOT NULL, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`deductionId`) REFERENCES `deductions`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_deductions_sessionId` " +
                "ON `session_deductions` (`sessionId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_deductions_deductionId` " +
                "ON `session_deductions` (`deductionId`)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_session_deductions_sessionId_deductionId` " +
                "ON `session_deductions` (`sessionId`, `deductionId`)"
        )
    }
}

/**
 * 4 -> 5: persisted open trips. `trips.platformId` becomes nullable (amount and payment already
 * were) so a ride in progress can be stored with only its start time. SQLite cannot drop a NOT NULL
 * constraint, so `trips` is rebuilt: same columns, ids, values, foreign keys and indexes.
 */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `trips_new` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`sessionId` INTEGER NOT NULL, " +
                "`platformId` INTEGER, " +
                "`startedAt` INTEGER NOT NULL, " +
                "`endedAt` INTEGER, " +
                "`amountMinor` INTEGER, " +
                "`paymentMethod` TEXT, " +
                "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`platformId`) REFERENCES `platforms`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )"
        )
        db.execSQL(
            "INSERT INTO trips_new (id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod) " +
                "SELECT id, sessionId, platformId, startedAt, endedAt, amountMinor, paymentMethod FROM trips"
        )
        db.execSQL("DROP TABLE `trips`")
        db.execSQL("ALTER TABLE `trips_new` RENAME TO `trips`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_sessionId` ON `trips` (`sessionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_trips_platformId` ON `trips` (`platformId`)")
    }
}
