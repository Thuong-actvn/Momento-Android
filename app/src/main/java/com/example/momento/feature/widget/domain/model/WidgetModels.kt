package com.example.momento.feature.widget.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class WidgetFriend(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class WidgetPhoto(
    val id: String,
    val friendId: String,
    val imageUrl: String,
    val caption: String?,
    val createdAtEpochMillis: Long,
)

@Serializable
data class WidgetSnapshot(
    val photos: List<WidgetPhoto> = emptyList(),
    val friends: List<WidgetFriend> = emptyList(),
    val updatedAtEpochMillis: Long,
)
