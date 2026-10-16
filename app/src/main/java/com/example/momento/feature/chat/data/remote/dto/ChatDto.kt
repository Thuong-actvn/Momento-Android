package com.example.momento.feature.chat.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ChatRealtimeEventDto(
    val type: String? = null,
    val payload: JsonElement? = null,
)

@Serializable
data class ConversationSummaryDto(
    val conversationId: String? = null,
    val otherUserId: String? = null,
    val otherUserDisplayName: String? = null,
    val otherUserAvatarUrl: String? = null,
    val lastMessageContent: String? = null,
    val lastMessageAt: String? = null,
)

@Serializable
data class MessageResponseDto(
    val id: String? = null,
    val conversationId: String? = null,
    val senderId: String? = null,
    val senderDisplayName: String? = null,
    val type: String? = null,
    val content: String? = null,
    val referencePhotoId: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class MessagePageDto(
    val data: List<MessageResponseDto>? = null,
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
)

@Serializable
data class SendChatMessageRequest(val content: String)
