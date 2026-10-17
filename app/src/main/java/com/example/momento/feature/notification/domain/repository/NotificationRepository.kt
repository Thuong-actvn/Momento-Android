package com.example.momento.feature.notification.domain.repository

import com.example.momento.feature.notification.domain.model.NotificationPage

interface NotificationRepository {
    /** Omit cursor for the first page; pass nextCursor back unchanged for subsequent pages. */
    suspend fun getNotifications(
        cursor: String? = null,
        limit: Int = 20,
    ): Result<NotificationPage>

    suspend fun markAsRead(id: String): Result<Unit>

    /** Applies to all notifications belonging to the current user, including unloaded pages. */
    suspend fun markAllAsRead(): Result<Unit>
}
