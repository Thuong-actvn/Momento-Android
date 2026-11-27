package com.example.momento.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.momento.feature.auth.data.local.OfflineSessionDao
import com.example.momento.feature.auth.data.local.OfflineSessionEntity
import com.example.momento.feature.chat.data.local.ChatDao
import com.example.momento.feature.chat.data.local.ChatMessageEntity
import com.example.momento.feature.chat.data.local.ChatSyncStateEntity
import com.example.momento.feature.chat.data.local.ConversationEntity
import com.example.momento.feature.moment.data.local.CachedMomentEntity
import com.example.momento.feature.moment.data.local.CachedMomentFriendEntity
import com.example.momento.feature.moment.data.local.MomentCacheDao
import com.example.momento.feature.moment.data.local.MyJournalSyncStateEntity

@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        ChatSyncStateEntity::class,
        OfflineSessionEntity::class,
        CachedMomentEntity::class,
        MyJournalSyncStateEntity::class,
        CachedMomentFriendEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MomentoDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun offlineSessionDao(): OfflineSessionDao
    abstract fun momentCacheDao(): MomentCacheDao
}
