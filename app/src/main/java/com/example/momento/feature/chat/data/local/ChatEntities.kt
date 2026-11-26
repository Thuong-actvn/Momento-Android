package com.example.momento.feature.chat.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.example.momento.feature.chat.domain.model.ChatMessageType

@Entity(
    tableName = "conversations",
    primaryKeys = ["accountId", "conversationId"],
    indices = [Index(value = ["accountId", "peerId"], unique = true)],
)
data class ConversationEntity(
    val accountId: String,
    val conversationId: String,
    val peerId: String,
    val peerDisplayName: String?,
    val peerAvatarUrl: String?,
    val lastMessageContent: String?,
    val lastMessageEpochSecond: Long?,
    val lastMessageNano: Int,
)

@Entity(
    tableName = "messages",
    primaryKeys = ["accountId", "messageId"],
    indices = [Index(value = ["accountId", "conversationId", "createdAtEpochSecond", "createdAtNano"])],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["accountId", "conversationId"],
            childColumns = ["accountId", "conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChatMessageEntity(
    val accountId: String,
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val senderDisplayName: String?,
    val type: ChatMessageType,
    val content: String,
    val referencePhotoId: String?,
    val createdAtEpochSecond: Long?,
    val createdAtNano: Int,
    // Payloads without a timestamp stay in arrival order until REST supplies one.
    val arrivalOrder: Long,
)

@Entity(tableName = "chat_sync_state", primaryKeys = ["accountId", "peerId"])
data class ChatSyncStateEntity(
    val accountId: String,
    val peerId: String,
    val lastSyncedMessageId: String?,
    val nextOlderCursor: String?,
    val hasMore: Boolean,
)
