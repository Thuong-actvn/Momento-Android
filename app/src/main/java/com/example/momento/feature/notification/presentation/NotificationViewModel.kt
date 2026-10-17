package com.example.momento.feature.notification.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.feature.notification.data.remote.NotificationError
import com.example.momento.feature.notification.data.remote.NotificationException
import com.example.momento.feature.notification.domain.model.NotificationPage
import com.example.momento.feature.notification.domain.model.NotificationType
import com.example.momento.feature.notification.domain.repository.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Scoped to the Notifications back stack entry; leaving the graph cancels its requests. */
@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationUiState())
    val state = _state.asStateFlow()

    private var pageJob: Job? = null
    private var nextCursor: String? = null
    private var nextEventId = 0L
    private val readIds = mutableSetOf<String>()

    init {
        refresh()
    }

    fun refresh() {
        loadPage(append = false)
    }

    fun retry() {
        if (_state.value.loadMoreError != null) {
            retryLoadMore()
        } else {
            refresh()
        }
    }

    fun loadMore() {
        if (_state.value.loadMoreError != null) return
        loadPage(append = true)
    }

    fun retryLoadMore() {
        loadPage(append = true)
    }

    fun onNotificationClick(id: String) {
        val current = _state.value
        val item = current.items.find { it.id == id } ?: return
        if (current.openFriendsRequestId != null) return

        if (item.type == NotificationType.FRIEND_REQUEST || item.type == NotificationType.FRIEND_ACCEPTED) {
            val requestId = ++nextEventId
            _state.update { it.copy(openFriendsRequestId = requestId) }
        }
        markAsRead(id)
    }

    fun consumeOpenFriendsRequest(requestId: Long): Boolean {
        if (_state.value.openFriendsRequestId != requestId) return false
        _state.update { it.copy(openFriendsRequestId = null) }
        return true
    }

    fun clearMessage(messageId: Long) {
        if (_state.value.message?.id == messageId) {
            _state.update { it.copy(message = null) }
        }
    }

    fun markAsRead(id: String) {
        val current = _state.value
        val item = current.items.find { it.id == id } ?: return
        if (item.isRead || id in current.readingIds || current.isMarkingAllAsRead) return

        _state.update { it.copy(readingIds = it.readingIds + id) }
        viewModelScope.launch {
            val result = repository.markAsRead(id)
            currentCoroutineContext().ensureActive()

            _state.update { it.copy(readingIds = it.readingIds - id) }
            result.fold(
                onSuccess = {
                    readIds.add(id)
                    _state.update { state ->
                        state.copy(
                            items = state.items.map { item ->
                                if (item.id == id) item.copy(isRead = true) else item
                            },
                        )
                    }
                },
                onFailure = { handleWriteError(it, R.string.notification_read_error) },
            )
        }
    }

    fun markAllAsRead() {
        if (!_state.value.canMarkAllAsRead) return

        pageJob?.cancel()
        _state.update {
            it.copy(
                isMarkingAllAsRead = true,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
            )
        }

        viewModelScope.launch {
            val result = repository.markAllAsRead()
            currentCoroutineContext().ensureActive()

            _state.update { it.copy(isMarkingAllAsRead = false) }
            result.fold(
                onSuccess = {
                    readIds.addAll(_state.value.items.map { it.id })
                    _state.update { state ->
                        state.copy(
                            items = state.items.map { it.copy(isRead = true) },
                        )
                    }
                    showMessage(R.string.notification_read_all_success)
                },
                onFailure = { handleWriteError(it, R.string.notification_read_all_error) },
            )
        }
    }

    private fun loadPage(append: Boolean) {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isMarkingAllAsRead) return
        if (append) {
            if (!current.hasMore || nextCursor == null || current.isLoadingMore) return
            if (current.error != null) return
        }

        pageJob?.cancel()
        val cursor = if (append) nextCursor else null
        _state.update {
            it.copy(
                isLoading = !append && !current.hasLoaded,
                isRefreshing = !append && current.hasLoaded,
                isLoadingMore = append,
                error = null,
                loadMoreError = null,
            )
        }

        pageJob = viewModelScope.launch {
            val result = repository.getNotifications(cursor, limit = 20)
            currentCoroutineContext().ensureActive()

            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                )
            }
            result.fold(
                onSuccess = { page ->
                    if (page.hasMore && (page.nextCursor.isNullOrBlank() || page.nextCursor == cursor)) {
                        handlePageError(append, NotificationException(NotificationError.INVALID_RESPONSE))
                    } else {
                        pageLoaded(append, page)
                    }
                },
                onFailure = { handlePageError(append, it) },
            )
        }
    }

    private fun pageLoaded(append: Boolean, page: NotificationPage) {
        nextCursor = page.nextCursor
        readIds.addAll(page.data.filter { it.isRead }.map { it.id })
        _state.update { current ->
            val items = if (append) current.items + page.data else page.data
            current.copy(
                items = items.distinctBy { it.id }.map {
                    if (it.id in readIds) it.copy(isRead = true) else it
                },
                hasLoaded = true,
                hasMore = page.hasMore,
            )
        }
    }

    private fun handlePageError(append: Boolean, error: Throwable) {
        if (clearIfSessionError(error)) return
        _state.update {
            if (append) {
                it.copy(
                    loadMoreError = errorMessage(error, R.string.notification_load_more_error),
                )
            } else {
                it.copy(
                    error = errorMessage(error, R.string.notification_load_error),
                )
            }
        }
    }

    private fun handleWriteError(error: Throwable, @StringRes fallback: Int) {
        if (!clearIfSessionError(error)) {
            showMessage(errorMessage(error, fallback))
        }
    }

    private fun clearIfSessionError(error: Throwable): Boolean {
        val reason = (error as? NotificationException)?.reason
        if (reason != NotificationError.UNAUTHENTICATED && reason != NotificationError.SESSION_CHANGED) {
            return false
        }

        viewModelScope.coroutineContext.cancelChildren()
        pageJob = null
        nextCursor = null
        readIds.clear()
        _state.value = NotificationUiState(
            error = R.string.notification_session_error,
        )
        return true
    }

    @StringRes
    private fun errorMessage(error: Throwable, @StringRes fallback: Int): Int {
        return when ((error as? NotificationException)?.reason) {
            NotificationError.NETWORK -> R.string.notification_network_error
            NotificationError.SERVER -> R.string.notification_server_error
            else -> fallback
        }
    }

    private fun showMessage(@StringRes text: Int) {
        val message = NotificationMessage(
            id = ++nextEventId,
            text = text,
        )
        _state.update { it.copy(message = message) }
    }
}
