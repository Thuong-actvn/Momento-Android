package com.example.momento.feature.moment.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.momento.feature.moment.domain.model.AudienceType

@Entity(
    tableName = "journal_my_photos",
    primaryKeys = ["accountId", "photoId"],
    indices = [Index(value = ["accountId", "createdAtEpochSecond", "createdAtNano", "photoId"])],
)
data class MyJournalPhotoEntity(
    val accountId: String,
    val photoId: String,
    val imageUrl: String,
    val caption: String?,
    val audienceType: AudienceType,
    val metadataJson: String?,
    val createdAtEpochSecond: Long?,
    val createdAtNano: Int,
    val previewRelativePath: String? = null,
)

@Entity(tableName = "journal_my_photo_sync_state")
data class MyJournalSyncStateEntity(
    @PrimaryKey val accountId: String,
    val nextCursor: String?,
    val hasMore: Boolean,
)
