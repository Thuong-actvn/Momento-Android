package com.example.momento.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.momento.feature.chat.data.local.ChatDao
import com.example.momento.feature.chat.data.local.ChatMessageEntity
import com.example.momento.feature.chat.data.local.ChatSyncStateEntity
import com.example.momento.feature.chat.data.local.ConversationEntity

@Database(
    entities = [ConversationEntity::class, ChatMessageEntity::class, ChatSyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MomentoDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
