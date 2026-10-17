package com.example.momento.feature.notification.presentation

import androidx.annotation.StringRes
import com.example.momento.feature.notification.domain.model.Notification

data class NotificationUiState(
    val items: List<Notification> = emptyList(),
    val hasLoaded: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    @param:StringRes val error: Int? = null,
    @param:StringRes val loadMoreError: Int? = null,
    val readingIds: Set<String> = emptySet(),
    val isMarkingAllAsRead: Boolean = false,
    val message: NotificationMessage? = null,
    val openFriendsRequestId: Long? = null,
) {
    val canMarkAllAsRead: Boolean
        get() = items.isNotEmpty() && !isMarkingAllAsRead && readingIds.isEmpty()
}

/** Retained until the route shows and acknowledges it, including while another screen is open. */
data class NotificationMessage(
    val id: Long,
    @param:StringRes val text: Int,
)
