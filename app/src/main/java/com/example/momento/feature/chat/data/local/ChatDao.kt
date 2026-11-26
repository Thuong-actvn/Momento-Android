package com.example.momento.feature.chat.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query(
        """
        SELECT * FROM conversations
        WHERE accountId = :accountId
        ORDER BY lastMessageEpochSecond DESC, lastMessageNano DESC, peerId ASC
        """,
    )
    fun observeConversations(accountId: String): Flow<List<ConversationEntity>>

    @Query(
        """
        SELECT messages.* FROM messages
        INNER JOIN conversations
            ON messages.accountId = conversations.accountId
            AND messages.conversationId = conversations.conversationId
        WHERE conversations.accountId = :accountId AND conversations.peerId = :peerId
        ORDER BY messages.createdAtEpochSecond IS NULL,
            messages.createdAtEpochSecond,
            messages.createdAtNano,
            CASE WHEN messages.createdAtEpochSecond IS NULL THEN messages.arrivalOrder END,
            messages.messageId
        """,
    )
    fun observeMessages(accountId: String, peerId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_sync_state WHERE accountId = :accountId AND peerId = :peerId")
    suspend fun getSyncState(accountId: String, peerId: String): ChatSyncStateEntity?

    @Upsert
    suspend fun upsertConversations(conversations: List<ConversationEntity>)

    @Upsert
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Upsert
    suspend fun upsertSyncState(syncState: ChatSyncStateEntity)
}
