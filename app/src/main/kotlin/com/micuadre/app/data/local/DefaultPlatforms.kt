package com.micuadre.app.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Platforms every install starts with. [callback] seeds them only when the database file is
 * created (fresh install); upgrades go through [MIGRATION_2_3], which inserts them itself, so
 * they are never seeded twice.
 */
object DefaultPlatforms {

    /** Display names in creation order. The migration relies on InDriver being id 1 and Didi id 2. */
    val NAMES: List<String> = listOf("InDriver", "Didi")

    fun insertInto(db: SupportSQLiteDatabase, createdAt: Long) {
        NAMES.forEach { name ->
            db.execSQL(
                "INSERT INTO platforms (name, visible, createdAt, deletedAt) VALUES (?, 1, ?, NULL)",
                arrayOf<Any?>(name, createdAt)
            )
        }
    }

    val callback: RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            insertInto(db, System.currentTimeMillis())
        }
    }
}
