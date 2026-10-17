package com.example.momento.feature.notification.data.remote.dto

import com.example.momento.feature.notification.domain.model.Notification
import com.example.momento.feature.notification.domain.model.NotificationPage
import com.example.momento.feature.notification.domain.model.NotificationType
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class NotificationResponseDto(
    val id: String,
    val type: String,
    val payload: JsonObject? = null,
    val isRead: Boolean,
    val createdAt: String,
)

@Serializable
data class NotificationPageDto(
    val data: List<NotificationResponseDto>,
    val nextCursor: String? = null,
    val hasMore: Boolean,
)

fun NotificationResponseDto.toDomain(): Notification = Notification(
    id = id,
    type = when (type) {
        "FRIEND_REQUEST" -> NotificationType.FRIEND_REQUEST
        "FRIEND_ACCEPTED" -> NotificationType.FRIEND_ACCEPTED
        else -> NotificationType.UNKNOWN
    },
    actorDisplayName = payload.stringOrNull("actorDisplayName"),
    friendId = payload.stringOrNull("friendId"),
    isRead = isRead,
    createdAt = Instant.parse(createdAt),
)

fun NotificationPageDto.toDomain(): NotificationPage = NotificationPage(
    data = data.map { it.toDomain() },
    nextCursor = nextCursor,
    hasMore = hasMore,
)

private fun JsonObject?.stringOrNull(key: String): String? {
    val value = this?.get(key)
    if (value !is JsonPrimitive || !value.isString) {
        return null
    }

    val text = value.content.trim()
    if (text.isEmpty()) {
        return null
    }

    return text
}
