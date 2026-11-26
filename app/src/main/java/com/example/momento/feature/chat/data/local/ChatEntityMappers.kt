package com.example.momento.feature.chat.data.local

import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ConversationSummary
import java.time.Instant

internal fun ConversationSummary.toEntity(accountId: String): ConversationEntity {
    return ConversationEntity(
        accountId = accountId,
        conversationId = conversationId,
        peerId = otherUserId,
        peerDisplayName = otherUserDisplayName,
        peerAvatarUrl = otherUserAvatarUrl,
        lastMessageContent = lastMessageContent,
        lastMessageEpochSecond = lastMessageAt?.epochSecond,
        lastMessageNano = lastMessageAt?.nano ?: 0,
    )
}

internal fun ConversationEntity.toDomain(): ConversationSummary {
    return ConversationSummary(
        conversationId = conversationId,
        otherUserId = peerId,
        otherUserDisplayName = peerDisplayName,
        otherUserAvatarUrl = peerAvatarUrl,
        lastMessageContent = lastMessageContent,
        lastMessageAt = toInstant(lastMessageEpochSecond, lastMessageNano),
    )
}

internal fun ChatMessage.toEntity(accountId: String, arrivalOrder: Long): ChatMessageEntity {
    return ChatMessageEntity(
        accountId = accountId,
        messageId = id,
        conversationId = conversationId,
        senderId = senderId,
        senderDisplayName = senderDisplayName,
        type = type,
        content = content,
        referencePhotoId = referencePhotoId,
        createdAtEpochSecond = createdAt?.epochSecond,
        createdAtNano = createdAt?.nano ?: 0,
        arrivalOrder = arrivalOrder,
    )
}

internal fun ChatMessageEntity.toDomain(): ChatMessage {
    return ChatMessage(
        id = messageId,
        conversationId = conversationId,
        senderId = senderId,
        senderDisplayName = senderDisplayName,
        type = type,
        content = content,
        referencePhotoId = referencePhotoId,
        createdAt = toInstant(createdAtEpochSecond, createdAtNano),
    )
}

private fun toInstant(epochSecond: Long?, nano: Int): Instant? {
    if (epochSecond == null) return null

    return Instant.ofEpochSecond(epochSecond, nano.toLong())
}
