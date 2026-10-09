package com.example.momento.feature.friend.domain.repository

import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.domain.model.FriendRequest
import com.example.momento.feature.friend.domain.model.FriendRequestAction

interface FriendRepository {
    suspend fun getFriends(): Result<List<PublicProfile>>

    /** The backend exposes incoming pending requests only. */
    suspend fun getPendingRequests(): Result<List<FriendRequest>>

    /** Username substring search; pages are zero-based and have no total count. */
    suspend fun searchUsers(
        query: String,
        page: Int = 0,
        limit: Int = 20,
    ): Result<List<PublicProfile>>

    /** Success may mean sent OR auto-accepted. Reload relationships to distinguish them. */
    suspend fun sendRequest(addresseeId: String): Result<Unit>

    suspend fun respondRequest(requestId: String, action: FriendRequestAction): Result<Unit>

    suspend fun removeFriend(friendUserId: String): Result<Unit>
}
