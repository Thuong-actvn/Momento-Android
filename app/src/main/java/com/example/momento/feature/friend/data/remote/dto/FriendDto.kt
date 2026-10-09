package com.example.momento.feature.friend.data.remote.dto

import com.example.momento.core.user.data.remote.dto.PublicProfileDto
import com.example.momento.core.user.data.remote.dto.toDomain
import com.example.momento.feature.friend.domain.model.FriendRequest
import kotlinx.serialization.Serializable

@Serializable
data class FriendRequestDto(
    val requestId: String,
    val requester: PublicProfileDto,
)

fun FriendRequestDto.toDomain(): FriendRequest = FriendRequest(
    requestId = requestId,
    requester = requester.toDomain(),
)

@Serializable
data class SendFriendRequest(
    val addresseeId: String,
)

@Serializable
data class RespondFriendRequest(
    val action: String,
)
