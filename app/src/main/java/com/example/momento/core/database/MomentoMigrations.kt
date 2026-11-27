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

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS moment_cache (
                accountId TEXT NOT NULL,
                photoId TEXT NOT NULL,
                imageUrl TEXT NOT NULL,
                caption TEXT,
                audienceType TEXT NOT NULL,
                metadataJson TEXT,
                createdAtEpochSecond INTEGER,
                createdAtNano INTEGER NOT NULL,
                previewRelativePath TEXT,
                ownerId TEXT NOT NULL,
                cachedAt INTEGER NOT NULL,
                PRIMARY KEY (accountId, photoId)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO moment_cache
                (accountId, photoId, imageUrl, caption, audienceType, metadataJson,
                createdAtEpochSecond, createdAtNano, previewRelativePath, ownerId, cachedAt)
            SELECT accountId, photoId, imageUrl, caption, audienceType, metadataJson,
                createdAtEpochSecond, createdAtNano, previewRelativePath, accountId, 0
            FROM journal_my_photos
            """.trimIndent()
        )
        db.execSQL("DROP TABLE journal_my_photos")
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
                index_moment_cache_accountId_createdAtEpochSecond_createdAtNano_photoId
            ON moment_cache (accountId, createdAtEpochSecond, createdAtNano, photoId)
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS moment_cache_friends (
                accountId TEXT NOT NULL,
                id TEXT NOT NULL,
                username TEXT NOT NULL,
                displayName TEXT,
                avatarUrl TEXT,
                createdAt TEXT NOT NULL,
                PRIMARY KEY (accountId, id)
            )
            """.trimIndent()
        )
    }
}
