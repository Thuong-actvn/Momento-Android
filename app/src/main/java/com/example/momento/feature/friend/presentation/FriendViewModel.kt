package com.example.momento.feature.friend.presentation

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.data.remote.FriendError
import com.example.momento.feature.friend.data.remote.FriendException
import com.example.momento.feature.friend.domain.repository.FriendRepository
import com.example.momento.feature.friend.domain.model.FriendRequest
import com.example.momento.feature.friend.domain.model.FriendRequestAction
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FriendUiState(
    val friends: List<PublicProfile> = emptyList(),
    val hasLoaded: Boolean = false,
    val isRefreshing: Boolean = false,
    @param:StringRes val error: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<PublicProfile> = emptyList(),
    val hasSearched: Boolean = false,
    val isSearching: Boolean = false,
    val hasMoreSearchResults: Boolean = false,
    val isLoadingMoreResults: Boolean = false,
    @param:StringRes val searchError: Int? = null,
    @param:StringRes val loadMoreError: Int? = null,
    val sendingRequestIds: Set<String> = emptySet(),
    // Local acknowledgements only; the backend has no outgoing-request list.
    val requestedUserIds: Set<String> = emptySet(),
    val requestErrors: Map<String, Int> = emptyMap(),
    val pendingRequests: List<FriendRequest> = emptyList(),
    val hasLoadedRequests: Boolean = false,
    val isRefreshingRequests: Boolean = false,
    @param:StringRes val pendingError: Int? = null,
    val respondingRequestIds: Set<String> = emptySet(),
    val responseErrors: Map<String, Int> = emptyMap(),
)

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val userStore: CurrentUserStore,
) : ViewModel() {
    private val _state = MutableStateFlow(FriendUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var pendingJob: Job? = null
    private val responseJobs = mutableMapOf<String, Job>()
    private var relationshipVersion = 0L
    private var searchJob: Job? = null
    private val requestJobs = mutableMapOf<String, Job>()
    private var searchVersion = 0L
    private var accountVersion = 0L
    private var nextSearchPage = 0
    private var ownerId = userStore.currentUser.value?.id

    init {
        viewModelScope.launch {
            userStore.currentUser.map { it?.id }.distinctUntilChanged().collect { userId ->
                if (userId != ownerId) {
                    cancelOperations()
                    ownerId = userId
                    // A new account must never see the previous account's friends.
                    _state.value = FriendUiState()
                    if (userId != null) refreshFriends()
                    else _state.update { it.copy(error = R.string.friend_session_error) }
                }
            }
        }
    }

    fun refreshFriends() {
        loadFriends(force = false)
        refreshRequests()
    }

    fun refreshRequests() = loadRequests(force = false)

    private fun refreshRelationships() {
        // Reads started before a confirmed write must not restore obsolete relationships.
        relationshipVersion++
        loadFriends(force = true)
        loadRequests(force = true)
    }

    private fun loadRequests(force: Boolean) {
        if (_state.value.isRefreshingRequests && !force) return
        pendingJob?.cancel()
        val version = relationshipVersion
        val account = accountVersion
        _state.update { it.copy(isRefreshingRequests = true, pendingError = null) }
        pendingJob = viewModelScope.launch {
            val result = repository.getPendingRequests()
            currentCoroutineContext().ensureActive()
            if (version != relationshipVersion || account != accountVersion) return@launch
            result.fold(
                onSuccess = { requests ->
                    val ids = requests.map { it.requestId }.toSet()
                    _state.update {
                        it.copy(pendingRequests = requests.distinctBy { request -> request.requestId },
                            hasLoadedRequests = true, isRefreshingRequests = false,
                            pendingError = null, responseErrors = it.responseErrors.filterKeys { id -> id in ids })
                    }
                },
                onFailure = { error ->
                    if (error.isSessionError()) clearSessionData()
                    else _state.update { it.copy(isRefreshingRequests = false,
                        pendingError = error.messageRes(R.string.friend_pending_load_error)) }
                },
            )
        }
    }

    fun respondRequest(requestId: String, action: FriendRequestAction) {
        val request = _state.value.pendingRequests.find { it.requestId == requestId } ?: return
        if (requestId in _state.value.respondingRequestIds ||
            request.requester.id in _state.value.sendingRequestIds) return
        val account = accountVersion
        _state.update { it.copy(respondingRequestIds = it.respondingRequestIds + requestId,
            responseErrors = it.responseErrors - requestId) }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.respondRequest(requestId, action)
                currentCoroutineContext().ensureActive()
                if (account != accountVersion) return@launch
                result.fold(
                    onSuccess = {
                        _state.update {
                            it.copy(pendingRequests = it.pendingRequests.filterNot { pending -> pending.requestId == requestId },
                                friends = if (action == FriendRequestAction.ACCEPT)
                                    (it.friends + request.requester).distinctBy { profile -> profile.id } else it.friends,
                                requestedUserIds = it.requestedUserIds - request.requester.id,
                                requestErrors = it.requestErrors - request.requester.id,
                                responseErrors = it.responseErrors - requestId)
                        }
                        refreshRelationships()
                    },
                    onFailure = { error ->
                        val exception = error as? FriendException
                        when {
                            error.isSessionError() -> clearSessionData()
                            exception?.statusCode == 409 -> _state.update {
                                it.copy(responseErrors = it.responseErrors + (requestId to R.string.friend_limit_error))
                            }
                            exception?.statusCode == 404 || exception?.backendCode in setOf(
                                "FRIEND_REQUEST_NOT_FOUND", "FRIEND_REQUEST_ALREADY_PROCESSED",
                                "FRIEND_REQUEST_NOT_PENDING",
                            ) -> refreshRelationships()
                            else -> _state.update { it.copy(responseErrors = it.responseErrors +
                                (requestId to error.messageRes(R.string.friend_response_error))) }
                        }
                    },
                )
            } finally {
                if (account == accountVersion) {
                    responseJobs.remove(requestId)
                    _state.update { it.copy(respondingRequestIds = it.respondingRequestIds - requestId) }
                }
            }
        }
        responseJobs[requestId] = job
        job.start()
    }

    fun refresh() {
        refreshFriends()
        if (_state.value.searchQuery.isNotBlank()) retrySearch()
    }

    private fun loadFriends(force: Boolean) {
        if (_state.value.isRefreshing && !force) return
        refreshJob?.cancel()
        val version = relationshipVersion
        val account = accountVersion
        _state.update { it.copy(isRefreshing = true, error = null) }
        refreshJob = viewModelScope.launch {
            val result = repository.getFriends()
            currentCoroutineContext().ensureActive()
            if (version != relationshipVersion || account != accountVersion) return@launch
            result.fold(
                onSuccess = { friends ->
                    val friendIds = friends.map { it.id }.toSet()
                    _state.update {
                        it.copy(
                            friends = friends,
                            hasLoaded = true,
                            isRefreshing = false,
                            error = null,
                            requestedUserIds = it.requestedUserIds - friendIds,
                            requestErrors = it.requestErrors - friendIds,
                        )
                    }
                },
                onFailure = { error ->
                    if (error.isSessionError()) {
                        clearSessionData()
                    } else {
                        // Keep existing data when a refresh fails.
                        _state.update {
                            it.copy(isRefreshing = false, error = error.messageRes(R.string.friend_load_error))
                        }
                    }
                },
            )
        }
    }

    fun onSearchQueryChange(query: String) {
        if (query == _state.value.searchQuery) return
        _state.update {
            it.copy(searchQuery = query, searchResults = emptyList(), hasSearched = false)
        }
        startSearch(immediate = false)
    }

    fun retrySearch() {
        startSearch(immediate = true)
    }

    private fun startSearch(immediate: Boolean) {
        searchJob?.cancel()
        val version = ++searchVersion
        val query = _state.value.searchQuery.trim()
        nextSearchPage = 0
        _state.update {
            it.copy(
                isSearching = query.isNotEmpty(),
                isLoadingMoreResults = false,
                hasMoreSearchResults = false,
                searchError = null,
                loadMoreError = null,
            )
        }
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            if (!immediate) delay(400)
            searchPage(query, page = 0, version = version)
        }
    }

    fun loadMoreSearchResults() {
        val current = _state.value
        if (current.searchQuery.isBlank() || !current.hasMoreSearchResults ||
            current.isSearching || current.isLoadingMoreResults
        ) return
        _state.update { it.copy(isLoadingMoreResults = true, loadMoreError = null) }
        val version = searchVersion
        val query = current.searchQuery.trim()
        val page = nextSearchPage
        searchJob = viewModelScope.launch { searchPage(query, page, version) }
    }

    private suspend fun searchPage(query: String, page: Int, version: Long) {
        val result = repository.searchUsers(query, page = page, limit = SEARCH_PAGE_SIZE)
        currentCoroutineContext().ensureActive()
        // Cancellation plus a version check also covers returning to an earlier query.
        if (version != searchVersion) return
        result.fold(
            onSuccess = { profiles ->
                nextSearchPage = page + 1
                _state.update {
                    it.copy(
                        searchResults = (if (page == 0) profiles else it.searchResults + profiles)
                            .filter { profile -> profile.id != ownerId }
                            .distinctBy { profile -> profile.id },
                        hasSearched = true,
                        isSearching = false,
                        isLoadingMoreResults = false,
                        hasMoreSearchResults = profiles.size == SEARCH_PAGE_SIZE,
                        searchError = null,
                        loadMoreError = null,
                    )
                }
            },
            onFailure = { error ->
                if (error.isSessionError()) {
                    clearSessionData()
                } else {
                    val message = error.messageRes(R.string.friend_search_error)
                    _state.update {
                        it.copy(
                            isSearching = false,
                            isLoadingMoreResults = false,
                            searchError = if (page == 0) message else null,
                            loadMoreError = if (page == 0) null else message,
                        )
                    }
                }
            },
        )
    }

    fun sendRequest(userId: String) {
        val current = _state.value
        if (userId == ownerId || current.searchResults.none { it.id == userId } ||
            current.pendingRequests.any { it.requester.id == userId } ||
            current.friends.any { it.id == userId } || userId in current.requestedUserIds ||
            userId in current.sendingRequestIds
        ) return
        val version = accountVersion
        _state.update {
            it.copy(sendingRequestIds = it.sendingRequestIds + userId, requestErrors = it.requestErrors - userId)
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = repository.sendRequest(userId)
                currentCoroutineContext().ensureActive()
                if (version != accountVersion) return@launch
                result.fold(
                    onSuccess = {
                        _state.update { it.copy(requestedUserIds = it.requestedUserIds + userId) }
                        // A reverse pending invitation may have been auto-accepted.
                        refreshRelationships()
                    },
                    onFailure = { error ->
                        when {
                            error.isSessionError() -> clearSessionData()
                            (error as? FriendException)?.backendCode == "FRIEND_REQUEST_ALREADY_EXISTS" -> {
                                _state.update { it.copy(requestedUserIds = it.requestedUserIds + userId) }
                            }
                            else -> {
                                _state.update {
                                    it.copy(requestErrors = it.requestErrors +
                                        (userId to error.messageRes(R.string.friend_request_send_error)))
                                }
                                if ((error as? FriendException)?.reason == FriendError.REQUEST_FAILED) {
                                    loadFriends(force = true)
                                }
                            }
                        }
                    },
                )
            } finally {
                if (version == accountVersion) {
                    requestJobs.remove(userId)
                    _state.update { it.copy(sendingRequestIds = it.sendingRequestIds - userId) }
                }
            }
        }
        requestJobs[userId] = job
        job.start()
    }

    private fun clearSessionData() {
        cancelOperations()
        _state.value = FriendUiState(error = R.string.friend_session_error)
    }

    private fun cancelOperations() {
        accountVersion++
        relationshipVersion++
        searchVersion++
        refreshJob?.cancel()
        pendingJob?.cancel()
        responseJobs.values.forEach { it.cancel() }
        responseJobs.clear()
        searchJob?.cancel()
        requestJobs.values.forEach { it.cancel() }
        requestJobs.clear()
    }

    private fun Throwable.isSessionError(): Boolean {
        val reason = (this as? FriendException)?.reason
        return reason == FriendError.UNAUTHENTICATED || reason == FriendError.SESSION_CHANGED
    }

    @StringRes
    private fun Throwable.messageRes(@StringRes fallback: Int): Int = when {
        isSessionError() -> R.string.friend_session_error
        (this as? FriendException)?.reason == FriendError.NETWORK -> R.string.friend_network_error
        (this as? FriendException)?.backendCode in setOf(
            "FRIEND_LIMIT_EXCEEDED", "FRIEND_LIMIT_REACHED", "MAX_FRIENDS_REACHED",
        ) -> R.string.friend_limit_error
        else -> fallback
    }

    private companion object {
        const val SEARCH_PAGE_SIZE = 20
    }
}
