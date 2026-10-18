package com.micuadre.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 1 -> 2: adds `connected_intervals` (real connected time per session, pauses excluded) and
 * backfills one interval per existing session spanning the whole session, so old shifts keep
 * the "Conectado" value they used to show.
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
                "SELECT id, startedAt, endedAt FROM sessions"
        )
    }
}
