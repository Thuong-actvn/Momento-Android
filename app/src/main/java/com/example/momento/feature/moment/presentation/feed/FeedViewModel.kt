package com.example.momento.feature.moment.presentation.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.data.remote.FriendError
import com.example.momento.feature.friend.data.remote.FriendException
import com.example.momento.feature.friend.domain.repository.FriendRepository
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val moments: MomentRepository,
    private val friends: FriendRepository,
    private val userStore: CurrentUserStore,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState(isLoading = true))
    val state = _state.asStateFlow()

    private var feedJob: Job? = null
    private var friendsJob: Job? = null
    private var ownerId: String? = null

    init {
        viewModelScope.launch {
            userStore.currentUser.collect { user ->
                if (user == null) {
                    ownerId = null
                    clearSessionData()
                } else if (user.id != ownerId) {
                    feedJob?.cancel()
                    friendsJob?.cancel()
                    ownerId = user.id
                    _state.value = FeedUiState()
                    // These requests are independent: photos do not wait for names and avatars.
                    loadFeed()
                    loadFriends()
                } else {
                    // Profile edits update the owner badge without fetching photos again.
                    _state.update { current ->
                        current.copy(items = withOwners(current.items, current.friends))
                    }
                }
            }
        }
    }

    fun selectFilter(filter: FeedFilter) {
        val current = _state.value
        if (filter == current.filter) return
        if (filter is FeedFilter.Friend && current.friends.none { it.id == filter.id }) return

        _state.update { it.copy(filter = filter, message = null) }
        loadFeed()
    }

    fun retryFeed() {
        if (!_state.value.isLoading) loadFeed()
    }

    fun retryFriends() {
        if (!_state.value.isLoadingFriends) loadFriends()
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun loadFeed() {
        val userId = userStore.currentUser.value?.id
        if (userId == null) {
            clearSessionData()
            return
        }

        feedJob?.cancel()
        val filter = _state.value.filter
        _state.update { it.copy(items = emptyList(), isLoading = true, error = null) }
        feedJob = viewModelScope.launch {
            val result = when (filter) {
                FeedFilter.All -> moments.getFeed()
                FeedFilter.OnlyMe -> moments.getMyMoments()
                is FeedFilter.Friend -> moments.getFriendFeed(filter.id)
            }
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId || _state.value.filter != filter) {
                return@launch
            }

            result.onSuccess { page ->
                val items = page.data.map { FeedItem(moment = it) }
                _state.update { current ->
                    current.copy(
                        items = withOwners(items, current.friends),
                        isLoading = false,
                        error = null,
                    )
                }
            }.onFailure { error ->
                showFeedError(error, filter)
            }
        }
    }

    private fun loadFriends() {
        val userId = userStore.currentUser.value?.id
        if (userId == null) {
            clearSessionData()
            return
        }

        friendsJob?.cancel()
        _state.update { it.copy(isLoadingFriends = true) }
        friendsJob = viewModelScope.launch {
            val result = friends.getFriends()
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId) return@launch

            result.onSuccess { profiles ->
                showFriends(profiles)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val reason = (error as? FriendException)?.reason
                when (reason) {
                    FriendError.UNAUTHENTICATED, FriendError.SESSION_CHANGED -> clearSessionData()
                    else -> {
                        val message = if (reason == FriendError.NETWORK) {
                            R.string.feed_friends_network_error
                        } else {
                            R.string.feed_friends_error
                        }
                        _state.update { it.copy(isLoadingFriends = false, friendsError = message) }
                    }
                }
            }
        }
    }

    private fun showFriends(profiles: List<PublicProfile>) {
        val owners = profiles.distinctBy { it.id }.map { profile ->
            MomentOwner(
                id = profile.id,
                username = profile.username,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
            )
        }
        _state.update { current ->
            current.copy(
                friends = owners,
                items = withOwners(current.items, owners),
                isLoadingFriends = false,
                friendsError = null,
            )
        }

        val filter = _state.value.filter
        if (filter is FeedFilter.Friend && owners.none { it.id == filter.id }) {
            returnToAll()
        }
    }

    private fun withOwners(items: List<FeedItem>, friends: List<MomentOwner>): List<FeedItem> {
        val ownersById = friends.associateBy { it.id }.toMutableMap()
        val user = userStore.currentUser.value
        if (user != null) {
            ownersById[user.id] = MomentOwner(user.id, user.username, user.displayName, user.avatarUrl)
        }
        return items.map { item -> item.copy(owner = ownersById[item.moment.ownerId]) }
    }

    private fun showFeedError(error: Throwable, filter: FeedFilter) {
        if (error is CancellationException) throw error
        val exception = error as? MomentException
        // The current backend uses HTTP 400; Swagger documents 403 for this same code.
        if (filter is FeedFilter.Friend && exception?.backendCode == "NOT_FRIENDS") {
            _state.update { current ->
                current.copy(friends = current.friends.filterNot { it.id == filter.id })
            }
            returnToAll()
            loadFriends()
            return
        }

        val message = when (exception?.reason) {
            MomentError.UNAUTHENTICATED, MomentError.SESSION_CHANGED -> {
                clearSessionData()
                return
            }
            MomentError.NETWORK -> R.string.feed_network_error
            MomentError.SERVER -> R.string.feed_server_error
            else -> R.string.feed_error
        }
        _state.update { it.copy(isLoading = false, error = message) }
    }

    private fun returnToAll() {
        selectFilter(FeedFilter.All)
        _state.update { it.copy(message = R.string.feed_friend_unavailable) }
    }

    private fun clearSessionData() {
        feedJob?.cancel()
        friendsJob?.cancel()
        _state.value = FeedUiState(error = R.string.feed_session_error)
    }
}
