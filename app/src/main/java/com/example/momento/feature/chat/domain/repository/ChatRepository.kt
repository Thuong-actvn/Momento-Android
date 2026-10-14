package com.example.momento.feature.chat.domain.repository

import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ChatMessagePage
import com.example.momento.feature.chat.domain.model.ConversationSummary
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    /** Cached conversations for the current session, newest first. No network request. */
    val conversations: Flow<List<ConversationSummary>>

    /** Cached messages, oldest first; this does not create or fetch a conversation. */
    fun observeMessages(otherUserId: String): Flow<List<ChatMessage>>

    suspend fun getConversations(): Result<List<ConversationSummary>>

    /** Only RESOURCE_NOT_FOUND on the first page means an empty conversation. */
    suspend fun getMessages(
        otherUserId: String,
        cursor: String? = null,
        limit: Int = 20,
    ): Result<ChatMessagePage>

    /** Trims content, validates 1..2000 characters and never automatically retries a POST. */
    suspend fun sendText(otherUserId: String, content: String): Result<ChatMessage>

    /**
     * ownerId must come from the feed photo: it groups the returned message in the local cache.
     * Only content is sent in the body; the backend determines the recipient from photoId.
     */
    suspend fun replyToMoment(photoId: String, ownerId: String, content: String): Result<ChatMessage>
}
