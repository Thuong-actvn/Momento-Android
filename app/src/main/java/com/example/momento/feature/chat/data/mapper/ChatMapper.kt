package com.example.momento.feature.chat.data.mapper

import com.example.momento.feature.chat.data.remote.dto.ConversationSummaryDto
import com.example.momento.feature.chat.data.remote.dto.MessagePageDto
import com.example.momento.feature.chat.data.remote.dto.MessageResponseDto
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ChatMessagePage
import com.example.momento.feature.chat.domain.model.ChatMessageType
import com.example.momento.feature.chat.domain.model.ConversationSummary
import java.time.Instant
import java.util.UUID

internal fun backendUuid(value: String): String {
    val canonical = UUID.fromString(value).toString()
    require(canonical.equals(value, ignoreCase = true)) { "Expected a backend UUID" }
    return canonical
}

private fun required(value: String?, field: String): String =
    checkNotNull(value) { "Backend response is missing $field" }

fun ConversationSummaryDto.toDomain(): ConversationSummary = ConversationSummary(
    conversationId = backendUuid(required(conversationId, "conversationId")),
    otherUserId = backendUuid(required(otherUserId, "otherUserId")),
    otherUserDisplayName = otherUserDisplayName,
    otherUserAvatarUrl = otherUserAvatarUrl,
    lastMessageContent = lastMessageContent,
    lastMessageAt = lastMessageAt?.let(Instant::parse),
)

fun MessageResponseDto.toDomain(): ChatMessage {
    val messageType = ChatMessageType.valueOf(required(type, "type"))
    val photoId = referencePhotoId?.let(::backendUuid)
    require(messageType != ChatMessageType.PHOTO_COMMENT || photoId != null) {
        "A photo comment must reference a photo"
    }
    require(messageType != ChatMessageType.TEXT || photoId == null) {
        "A text message cannot reference a photo"
    }
    return ChatMessage(
        id = backendUuid(required(id, "id")),
        conversationId = backendUuid(required(conversationId, "conversationId")),
        senderId = backendUuid(required(senderId, "senderId")),
        senderDisplayName = senderDisplayName,
        type = messageType,
        content = required(content, "content"),
        referencePhotoId = photoId,
        createdAt = createdAt?.let(Instant::parse),
    )
}

fun MessagePageDto.toDomain(): ChatMessagePage {
    val messages = checkNotNull(data) { "Backend response is missing page data" }.map { it.toDomain() }
    val more = checkNotNull(hasMore) { "Backend response is missing hasMore" }
    require(!more || (!nextCursor.isNullOrBlank() && messages.isNotEmpty())) {
        "A page with more messages must provide data and a cursor"
    }
    return ChatMessagePage(messages, nextCursor, more)
}
