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

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS journal_my_photos (
                accountId TEXT NOT NULL,
                photoId TEXT NOT NULL,
                imageUrl TEXT NOT NULL,
                caption TEXT,
                audienceType TEXT NOT NULL,
                metadataJson TEXT,
                createdAtEpochSecond INTEGER,
                createdAtNano INTEGER NOT NULL,
                previewRelativePath TEXT,
                PRIMARY KEY (accountId, photoId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                index_journal_my_photos_accountId_createdAtEpochSecond_createdAtNano_photoId
            ON journal_my_photos (accountId, createdAtEpochSecond, createdAtNano, photoId)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS journal_my_photo_sync_state (
                accountId TEXT NOT NULL PRIMARY KEY,
                nextCursor TEXT,
                hasMore INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}
