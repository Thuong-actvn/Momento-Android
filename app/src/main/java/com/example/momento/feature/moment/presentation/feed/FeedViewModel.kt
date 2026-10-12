package com.example.momento.feature.moment.presentation.feed

import androidx.lifecycle.SavedStateHandle
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
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
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
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedUiState(isLoading = true))
    val state = _state.asStateFlow()

    private var feedJob: Job? = null
    private var friendsJob: Job? = null
    private var reactionJob: Job? = null
    private var ownerId: String? = null
    private var nextCursor: String? = null
    private val usedCursors = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            userStore.currentUser.collect { user ->
                if (user == null) {
                    ownerId = null
                    clearSessionData()
                } else if (user.id != ownerId) {
                    feedJob?.cancel()
                    friendsJob?.cancel()
                    reactionJob?.cancel()
                    ownerId = user.id
                    val sameUser = savedState.get<String>("feed_owner") == user.id
                    _state.value = FeedUiState(
                        filter = if (sameUser) restoredFilter() else FeedFilter.All,
                        visibleMomentId = if (sameUser) savedState["feed_moment"] else null,
                    )
                    savedState["feed_owner"] = user.id
                    savePosition()
                    // These requests are independent: photos do not wait for names and avatars.
                    loadFeed(FeedLoad.INITIAL)
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

        _state.update { it.copy(filter = filter, visibleMomentId = null, message = null) }
        savePosition()
        loadFeed(FeedLoad.INITIAL)
    }

    fun retryFeed() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        if (_state.value.items.isEmpty()) loadFeed(FeedLoad.INITIAL) else refresh()
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        loadFeed(FeedLoad.REFRESH)
        loadFriends()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadMoreError != null || current.error != null) return
        retryLoadMore()
    }

    fun retryLoadMore() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore || !current.hasMore) return
        val cursor = nextCursor
        if (cursor == null) {
            // A malformed first page has no usable cursor: retry from the start.
            refresh()
        } else {
            loadFeed(FeedLoad.MORE, cursor)
        }
    }

    fun showMoment(momentId: String) {
        if (_state.value.visibleMomentId == momentId) return
        if (_state.value.items.none { it.moment.id == momentId }) return
        _state.update { it.copy(visibleMomentId = momentId) }
        savePosition()
    }

    fun retryFriends() {
        if (!_state.value.isLoadingFriends) loadFriends()
    }

    fun sendReaction(momentId: String, emoji: String) {
        val current = _state.value
        if (current.sendingReactionId != null) return
        if (current.items.none { it.moment.id == momentId }) return
        if (emoji !in listOf("❤️", "😂", "😍", "😮", "🔥")) return
        val userId = userStore.currentUser.value?.id
        if (userId == null) {
            clearSessionData()
            return
        }

        _state.update { it.copy(sendingReactionId = momentId, message = null) }
        // Feed requests can be canceled independently without canceling or replaying this POST.
        reactionJob = viewModelScope.launch {
            val result = moments.reactToMoment(momentId, emoji)
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId) return@launch

            result.onSuccess {
                _state.update {
                    it.copy(
                        sendingReactionId = null,
                        sentReactions = it.sentReactions + (momentId to emoji),
                    )
                }
            }.onFailure { error ->
                showReactionError(error)
            }
        }
    }

    fun clearMessage(message: FeedMessage) {
        _state.update { current ->
            if (current.message === message) current.copy(message = null) else current
        }
    }

    private fun loadFeed(load: FeedLoad, cursor: String? = null) {
        val userId = userStore.currentUser.value?.id
        if (userId == null) {
            clearSessionData()
            return
        }

        feedJob?.cancel()
        val filter = _state.value.filter
        if (load == FeedLoad.INITIAL) {
            nextCursor = null
            usedCursors.clear()
        }
        _state.update { current ->
            current.copy(
                items = if (load == FeedLoad.INITIAL) emptyList() else current.items,
                isLoading = load == FeedLoad.INITIAL || current.items.isEmpty(),
                isRefreshing = load == FeedLoad.REFRESH,
                isLoadingMore = load == FeedLoad.MORE,
                hasMore = if (load == FeedLoad.INITIAL) false else current.hasMore,
                error = null,
                loadMoreError = null,
            )
        }
        feedJob = viewModelScope.launch {
            val result = when (filter) {
                FeedFilter.All -> moments.getFeed(cursor = cursor)
                FeedFilter.OnlyMe -> moments.getMyMoments(cursor = cursor)
                is FeedFilter.Friend -> moments.getFriendFeed(filter.id, cursor = cursor)
            }
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId || _state.value.filter != filter) {
                return@launch
            }

            result.onSuccess { page ->
                showPage(page, load, cursor)
            }.onFailure { error ->
                showFeedError(error, filter, load)
            }
        }
    }

    private fun showPage(page: CursorPage<Moment>, load: FeedLoad, requestedCursor: String?) {
        if (load != FeedLoad.MORE) usedCursors.clear()
        val cursorInvalid = page.hasMore && (
            page.nextCursor.isNullOrBlank() || page.nextCursor == requestedCursor ||
                page.nextCursor in usedCursors || page.data.isEmpty()
            )
        // Keep the last usable request cursor so an explicit retry can repeat that page.
        nextCursor = if (cursorInvalid) requestedCursor else page.nextCursor
        if (requestedCursor != null && !cursorInvalid) usedCursors.add(requestedCursor)

        _state.update { current ->
            val newItems = page.data.map { FeedItem(moment = it) }
            val items = if (load == FeedLoad.MORE) current.items + newItems else newItems
            val uniqueItems = withOwners(items.distinctBy { it.moment.id }, current.friends)
            val visibleId = if (load == FeedLoad.REFRESH) {
                uniqueItems.firstOrNull()?.moment?.id
            } else {
                uniqueItems.find { it.moment.id == current.visibleMomentId }?.moment?.id
                    ?: uniqueItems.firstOrNull()?.moment?.id
            }
            current.copy(
                items = uniqueItems,
                visibleMomentId = visibleId,
                pageVersion = if (load == FeedLoad.MORE) current.pageVersion else current.pageVersion + 1,
                hasMore = page.hasMore,
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = if (cursorInvalid && uniqueItems.isEmpty()) R.string.feed_pagination_error else null,
                loadMoreError = if (cursorInvalid && uniqueItems.isNotEmpty()) R.string.feed_pagination_error else null,
            )
        }
        savePosition()
    }

    private fun restoredFilter(): FeedFilter {
        return when (val value = savedState.get<String>("feed_filter")) {
            null, "all" -> FeedFilter.All
            "me" -> FeedFilter.OnlyMe
            else -> FeedFilter.Friend(value)
        }
    }

    private fun savePosition() {
        savedState["feed_filter"] = when (val filter = _state.value.filter) {
            FeedFilter.All -> "all"
            FeedFilter.OnlyMe -> "me"
            is FeedFilter.Friend -> filter.id
        }
        savedState["feed_moment"] = _state.value.visibleMomentId
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

    private fun showFeedError(error: Throwable, filter: FeedFilter, load: FeedLoad) {
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
        _state.update { current ->
            current.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                error = if (load == FeedLoad.MORE) null else message,
                loadMoreError = if (load == FeedLoad.MORE) message else null,
            )
        }
    }

    private fun returnToAll() {
        selectFilter(FeedFilter.All)
        _state.update { it.copy(message = FeedMessage(R.string.feed_friend_unavailable)) }
    }

    private fun showReactionError(error: Throwable) {
        if (error is CancellationException) throw error
        val exception = error as? MomentException
        if (exception?.reason == MomentError.UNAUTHENTICATED || exception?.reason == MomentError.SESSION_CHANGED) {
            clearSessionData()
            return
        }
        val message = when {
            exception?.backendCode in listOf("PHOTO_NOT_FOUND", "PHOTO_ACCESS_DENIED") -> {
                FeedMessage(R.string.feed_reaction_unavailable, canRefresh = true)
            }
            exception?.reason == MomentError.REQUEST_FAILED -> FeedMessage(R.string.feed_reaction_error)
            else -> FeedMessage(R.string.feed_reaction_unconfirmed)
        }
        _state.update { it.copy(sendingReactionId = null, message = message) }
    }

    private fun clearSessionData() {
        feedJob?.cancel()
        friendsJob?.cancel()
        reactionJob?.cancel()
        nextCursor = null
        usedCursors.clear()
        savedState["feed_owner"] = null
        _state.value = FeedUiState(error = R.string.feed_session_error)
        savePosition()
    }
}

private enum class FeedLoad {
    INITIAL,
    REFRESH,
    MORE,
}
