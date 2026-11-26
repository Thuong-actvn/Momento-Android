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

    @Query(
        """
        SELECT nextOlderCursor FROM chat_sync_state
        WHERE accountId = :accountId AND peerId = :peerId AND hasMore = 1
        """,
    )
    fun observeOlderCursor(accountId: String, peerId: String): Flow<String?>

    @Query("SELECT * FROM chat_sync_state WHERE accountId = :accountId AND peerId = :peerId")
    suspend fun getSyncState(accountId: String, peerId: String): ChatSyncStateEntity?

    @Query("SELECT * FROM conversations WHERE accountId = :accountId AND peerId = :peerId")
    suspend fun getConversation(accountId: String, peerId: String): ConversationEntity?

    @Query("SELECT peerId FROM conversations WHERE accountId = :accountId AND conversationId = :conversationId")
    suspend fun getPeerId(accountId: String, conversationId: String): String?

    @Query("SELECT * FROM messages WHERE accountId = :accountId AND messageId IN (:messageIds)")
    suspend fun getMessagesById(accountId: String, messageIds: List<String>): List<ChatMessageEntity>

    @Query(
        """
        SELECT * FROM messages
        WHERE accountId = :accountId AND conversationId = :conversationId
        ORDER BY createdAtEpochSecond IS NULL DESC,
            createdAtEpochSecond DESC,
            createdAtNano DESC,
            CASE WHEN createdAtEpochSecond IS NULL THEN arrivalOrder END DESC,
            messageId DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestMessage(accountId: String, conversationId: String): ChatMessageEntity?

    @Query(
        """
        SELECT COALESCE(MAX(arrivalOrder), 0) FROM messages
        WHERE accountId = :accountId AND conversationId = :conversationId
        """,
    )
    suspend fun getLatestArrivalOrder(accountId: String, conversationId: String): Long

    @Upsert
    suspend fun upsertConversations(conversations: List<ConversationEntity>)

    @Upsert
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Upsert
    suspend fun upsertSyncState(syncState: ChatSyncStateEntity)

    @Query("DELETE FROM conversations WHERE accountId = :accountId")
    suspend fun deleteConversations(accountId: String)

    @Query("DELETE FROM chat_sync_state WHERE accountId = :accountId")
    suspend fun deleteSyncState(accountId: String)
}
