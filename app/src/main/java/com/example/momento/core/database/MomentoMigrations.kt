package com.example.momento.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS offline_session (
                firebaseUid TEXT NOT NULL,
                backendUserId TEXT NOT NULL,
                displayName TEXT NOT NULL,
                slot INTEGER NOT NULL PRIMARY KEY
            )
            """.trimIndent()
        )
    }
}
