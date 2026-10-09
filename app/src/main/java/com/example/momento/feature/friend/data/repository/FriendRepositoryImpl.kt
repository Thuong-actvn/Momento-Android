package com.example.momento.feature.friend.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.data.remote.dto.toDomain
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.data.remote.FriendApi
import com.example.momento.feature.friend.data.remote.FriendError
import com.example.momento.feature.friend.data.remote.FriendException
import com.example.momento.feature.friend.data.remote.dto.RespondFriendRequest
import com.example.momento.feature.friend.data.remote.dto.SendFriendRequest
import com.example.momento.feature.friend.data.remote.dto.toDomain
import com.example.momento.feature.friend.data.remote.toFriendException
import com.example.momento.feature.friend.domain.model.FriendRequest
import com.example.momento.feature.friend.domain.model.FriendRequestAction
import com.example.momento.feature.friend.domain.repository.FriendRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class FriendRepositoryImpl @Inject constructor(
    private val api: FriendApi,
    private val userStore: CurrentUserStore,
) : FriendRepository {
    override suspend fun getFriends(): Result<List<PublicProfile>> = requestResult {
        api.getFriends().requireData().map { it.toDomain() }
    }

    override suspend fun getPendingRequests(): Result<List<FriendRequest>> = requestResult {
        api.getPendingRequests().requireData().map { it.toDomain() }
    }

    override suspend fun searchUsers(
        query: String,
        page: Int,
        limit: Int,
    ): Result<List<PublicProfile>> = requestResult {
        require(page >= 0) { "Search page must be nonnegative" }
        require(limit in 1..50) { "Search limit must be between 1 and 50" }
        val text = query.trim()
        if (text.isEmpty()) return@requestResult emptyList()
        api.searchUsers(text, page, limit).requireData().map { it.toDomain() }
    }

    override suspend fun sendRequest(addresseeId: String): Result<Unit> = requestResult {
        api.sendRequest(SendFriendRequest(addresseeId))
    }

    override suspend fun respondRequest(
        requestId: String,
        action: FriendRequestAction,
    ): Result<Unit> = requestResult {
        api.respondRequest(requestId, RespondFriendRequest(action.name))
    }

    override suspend fun removeFriend(friendUserId: String): Result<Unit> = requestResult {
        api.removeFriend(friendUserId)
    }

    private suspend fun <T> requestResult(block: suspend () -> T): Result<T> = suspendRunCatching {
        val session = try {
            userStore.requireSession()
        } catch (error: IllegalStateException) {
            throw FriendException(FriendError.UNAUTHENTICATED, cause = error)
        }
        try {
            block().also { checkSession(session) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // An old session must not publish either a result or an error to the new account.
            checkSession(session)
            throw error.toFriendException()
        }
    }

    private fun checkSession(session: CurrentUserStore.Session) {
        try {
            userStore.checkSession(session)
        } catch (error: IllegalStateException) {
            throw FriendException(FriendError.SESSION_CHANGED, cause = error)
        }
    }
}
