package com.example.momento.feature.auth.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "offline_session")
data class OfflineSessionEntity(
    val firebaseUid: String,
    val backendUserId: String,
    val displayName: String,
    @PrimaryKey val slot: Int = 0,
)

@Dao
interface OfflineSessionDao {
    @Query("SELECT * FROM offline_session WHERE slot = 0")
    suspend fun read(): OfflineSessionEntity?

    @Upsert
    suspend fun save(session: OfflineSessionEntity)

    @Query("DELETE FROM offline_session")
    suspend fun delete()
}
