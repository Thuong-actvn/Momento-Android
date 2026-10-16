package com.example.momento.feature.widget.data.remote

import com.example.momento.core.user.data.remote.dto.PublicProfileDto
import com.example.momento.feature.moment.data.remote.dto.toDomain
import com.example.momento.feature.widget.domain.model.WidgetFriend
import com.example.momento.feature.widget.domain.model.WidgetPhoto
import com.example.momento.feature.widget.domain.model.WidgetSnapshot

fun toWidgetSnapshot(
    items: List<WidgetItemDto>,
    friends: List<PublicProfileDto>,
    updatedAtEpochMillis: Long,
): WidgetSnapshot {
    val friendIds = friends.map { friend -> friend.id }.toSet()
    val newestFirst = compareByDescending<WidgetPhoto> { it.createdAtEpochMillis }
        .thenByDescending { it.id }

    // Sort before deduplication to keep the newest photo for each current friend.
    val photos = items
        .map { item -> item.toWidgetPhoto() }
        .filter { photo -> photo.friendId in friendIds }
        .sortedWith(newestFirst)
        .distinctBy { photo -> photo.friendId }

    val widgetFriends = friends
        .distinctBy { friend -> friend.id }
        .map { friend -> friend.toWidgetFriend() }

    return WidgetSnapshot(
        photos = photos,
        friends = widgetFriends,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun WidgetItemDto.toWidgetPhoto(): WidgetPhoto {
    val moment = photo.toDomain()
    require(friendId.isNotBlank() && friendId == moment.ownerId) {
        "Widget photo owner does not match friendId"
    }
    require(moment.id.isNotBlank() && moment.imageUrl.isNotBlank()) {
        "Invalid widget photo"
    }
    val createdAt = checkNotNull(moment.createdAt) {
        "Widget photo has no timestamp"
    }

    return WidgetPhoto(
        id = moment.id,
        friendId = friendId,
        imageUrl = moment.imageUrl,
        caption = moment.caption,
        createdAtEpochMillis = createdAt.toEpochMilli(),
    )
}

private fun PublicProfileDto.toWidgetFriend(): WidgetFriend {
    return WidgetFriend(
        id = id,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
    )
}
