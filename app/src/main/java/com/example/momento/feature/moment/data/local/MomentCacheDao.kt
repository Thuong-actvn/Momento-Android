package com.example.momento.feature.moment.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentCacheDao {
    @Query(
        """
        SELECT * FROM moment_cache
        WHERE accountId = :accountId AND ownerId = :accountId
        ORDER BY createdAtEpochSecond DESC, createdAtNano DESC, photoId DESC
        """
    )
    fun observePhotos(accountId: String): Flow<List<CachedMomentEntity>>

    @Upsert
    suspend fun upsertPhotos(photos: List<CachedMomentEntity>)

    @Query("SELECT * FROM moment_cache WHERE accountId = :accountId AND photoId IN (:photoIds)")
    suspend fun getPhotos(accountId: String, photoIds: List<String>): List<CachedMomentEntity>

    @Query(
        """
        SELECT * FROM moment_cache WHERE accountId = :accountId
            AND (:ownerId IS NULL OR ownerId = :ownerId)
            AND (:includeOwn OR ownerId != :accountId)
        ORDER BY createdAtEpochSecond DESC, createdAtNano DESC, photoId DESC
        """,
    )
    suspend fun getCachedPhotos(accountId: String, ownerId: String?, includeOwn: Boolean): List<CachedMomentEntity>

    @Query("SELECT photoId FROM moment_cache WHERE accountId = :accountId AND ownerId = :ownerId")
    suspend fun getPhotoIdsForOwner(accountId: String, ownerId: String): List<String>

    @Query("SELECT photoId FROM moment_cache WHERE accountId = :accountId AND ownerId != :accountId AND cachedAt < :cutoff")
    suspend fun getExpiredFriendPhotos(accountId: String, cutoff: Long): List<String>

    @Query(
        """
        SELECT photoId FROM moment_cache WHERE accountId = :accountId
        ORDER BY cachedAt DESC, createdAtEpochSecond DESC, createdAtNano DESC, photoId DESC
        LIMIT -1 OFFSET :keep
        """,
    )
    suspend fun getExcessPhotos(accountId: String, keep: Int): List<String>

    @Query("UPDATE moment_cache SET previewRelativePath = :path WHERE accountId = :accountId AND photoId = :photoId")
    suspend fun updatePreview(accountId: String, photoId: String, path: String)

    @Query("DELETE FROM moment_cache WHERE accountId = :accountId AND photoId IN (:photoIds)")
    suspend fun deletePhotos(accountId: String, photoIds: List<String>)

    @Query("DELETE FROM moment_cache WHERE accountId = :accountId")
    suspend fun deleteAccountPhotos(accountId: String)

    @Query("SELECT * FROM moment_cache_friends WHERE accountId = :accountId ORDER BY username")
    suspend fun getFriends(accountId: String): List<CachedMomentFriendEntity>

    @Upsert
    suspend fun upsertFriends(friends: List<CachedMomentFriendEntity>)

    @Query("DELETE FROM moment_cache_friends WHERE accountId = :accountId")
    suspend fun deleteFriends(accountId: String)

    @Query("DELETE FROM moment_cache_friends WHERE accountId = :accountId AND id = :ownerId")
    suspend fun deleteFriend(accountId: String, ownerId: String)

    @Query("SELECT * FROM journal_my_photo_sync_state WHERE accountId = :accountId")
    suspend fun getSyncState(accountId: String): MyJournalSyncStateEntity?

    @Upsert
    suspend fun upsertSyncState(state: MyJournalSyncStateEntity)

    @Query("DELETE FROM journal_my_photo_sync_state WHERE accountId = :accountId")
    suspend fun deleteSyncState(accountId: String)
}
