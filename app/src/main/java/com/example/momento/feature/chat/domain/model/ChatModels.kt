package com.example.momento.feature.chat.domain.model

import java.time.Instant

enum class ChatMessageType { TEXT, PHOTO_COMMENT }

data class ConversationSummary(
    val conversationId: String,
    val otherUserId: String,
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
    val lastMessageContent: String?,
    val lastMessageAt: Instant?,
)

data class ChatMessage(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val senderDisplayName: String?,
    val type: ChatMessageType,
    val content: String,
    val referencePhotoId: String?,
    // A send/event DTO may be built before Hibernate fills the creation timestamp.
    val createdAt: Instant?,
)

/** The API returns newest first. Its cursor is opaque and only pages toward older messages. */
data class ChatMessagePage(
    val data: List<ChatMessage>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
