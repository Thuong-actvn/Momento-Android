package com.example.momento.feature.notification.domain.model

import java.time.Instant

data class Notification(
    val id: String,
    val type: NotificationType,
    val actorDisplayName: String?,
    val friendId: String?,
    val isRead: Boolean,
    val createdAt: Instant,
)

/** Only friend events currently have a persisted notification history in the backend. */
enum class NotificationType { FRIEND_REQUEST, FRIEND_ACCEPTED, UNKNOWN }

data class NotificationPage(
    val data: List<Notification>,
    val nextCursor: String?,
    val hasMore: Boolean,
)
