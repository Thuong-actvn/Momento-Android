package com.example.momento.feature.friend.domain.model

import com.example.momento.core.user.domain.model.PublicProfile

/** An incoming, pending request. requestId is different from requester.id. */
data class FriendRequest(
    val requestId: String,
    val requester: PublicProfile,
)

enum class FriendRequestAction { ACCEPT, REJECT }
