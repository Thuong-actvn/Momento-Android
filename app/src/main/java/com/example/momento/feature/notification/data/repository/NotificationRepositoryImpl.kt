package com.example.momento.feature.notification.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.network.ApiException
import com.example.momento.core.network.ApiResponse
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.notification.data.remote.NotificationApi
import com.example.momento.feature.notification.data.remote.NotificationError
import com.example.momento.feature.notification.data.remote.NotificationException
import com.example.momento.feature.notification.data.remote.dto.toDomain
import com.example.momento.feature.notification.data.remote.toNotificationException
import com.example.momento.feature.notification.domain.model.NotificationPage
import com.example.momento.feature.notification.domain.repository.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
class NotificationRepositoryImpl @Inject constructor(
    private val api: NotificationApi,
    private val userStore: CurrentUserStore,
) : NotificationRepository {
    override suspend fun getNotifications(cursor: String?, limit: Int): Result<NotificationPage> =
        requestResult {
            require(limit in 1..50) { "Notification page size must be between 1 and 50" }
            require(cursor == null || cursor.isNotBlank()) { "Omit cursor for the first page" }
            api.getNotifications(cursor, limit).requireData().toDomain()
        }

    override suspend fun markAsRead(id: String): Result<Unit> = requestResult {
        require(id.isNotBlank()) { "Notification id must not be blank" }
        api.markAsRead(id).requireSuccess()
    }

    override suspend fun markAllAsRead(): Result<Unit> = requestResult {
        api.markAllAsRead().requireSuccess()
    }

    private suspend fun <T> requestResult(block: suspend () -> T): Result<T> = suspendRunCatching {
        val session = try {
            userStore.requireSession()
        } catch (error: IllegalStateException) {
            throw NotificationException(NotificationError.UNAUTHENTICATED, cause = error)
        }
        try {
            block().also { checkSession(session) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // An old request must not publish a result or an error to a different session.
            checkSession(session)
            throw error.toNotificationException()
        }
    }

    private fun checkSession(session: CurrentUserStore.Session) {
        try {
            userStore.checkSession(session)
        } catch (error: IllegalStateException) {
            throw NotificationException(NotificationError.SESSION_CHANGED, cause = error)
        }
    }

    private fun ApiResponse<Unit>.requireSuccess() {
        if (!success) {
            throw ApiException(error?.code, error?.message ?: "Backend request failed")
        }
    }
}
