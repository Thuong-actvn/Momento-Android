package com.example.momento.feature.moment.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MyJournalDao {
    @Query(
        """
        SELECT * FROM journal_my_photos
        WHERE accountId = :accountId
        ORDER BY createdAtEpochSecond DESC, createdAtNano DESC, photoId DESC
        """
    )
    fun observePhotos(accountId: String): Flow<List<MyJournalPhotoEntity>>

    @Upsert
    suspend fun upsertPhotos(photos: List<MyJournalPhotoEntity>)

    @Query("SELECT * FROM journal_my_photo_sync_state WHERE accountId = :accountId")
    suspend fun getSyncState(accountId: String): MyJournalSyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: MyJournalSyncStateEntity)
}
