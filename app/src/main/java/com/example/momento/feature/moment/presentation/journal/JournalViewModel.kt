package com.example.momento.feature.moment.presentation.journal

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
class JournalViewModel @Inject constructor(
    moments: MomentRepository,
    private val friends: FriendRepository,
    private val userStore: CurrentUserStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(JournalUiState(isLoading = true))
    val state = _state.asStateFlow()

    private var photosJob: Job? = null
    private var friendsJob: Job? = null
    private var ownerId: String? = null
    private val photoLoader = JournalPhotoLoader(moments)

    init {
        viewModelScope.launch {
            userStore.currentUser.collect { user ->
                if (user == null) {
                    clearSession()
                } else if (user.id != ownerId) {
                    photosJob?.cancel()
                    friendsJob?.cancel()
                    photoLoader.reset()
                    ownerId = user.id
                    val sameUser = savedState.get<String>("journal_owner") == user.id
                    _state.value = JournalUiState(
                        filter = if (sameUser) restoredFilter() else JournalFilter.All,
                    )
                    saveFilter()
                    loadPhotos(JournalLoad.INITIAL)
                    loadFriends()
                } else {
                    _state.update { current ->
                        JournalStateMapper.ownersUpdated(current = current, user = user)
                    }
                }
            }
        }
    }

    fun selectFilter(filter: JournalFilter) {
        val current = _state.value
        if (filter == current.filter) return
        if (filter is JournalFilter.Friend && current.friends.none { it.id == filter.id }) return

        _state.update { it.copy(filter = filter) }
        saveFilter()
        loadPhotos(JournalLoad.INITIAL)
    }

    fun retry() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        when {
            current.loadMoreError != null -> loadPhotos(JournalLoad.MORE)
            current.items.isEmpty() -> loadPhotos(JournalLoad.INITIAL)
            else -> refresh()
        }
    }

    fun refresh() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        loadPhotos(JournalLoad.REFRESH)
        loadFriends()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadMoreError != null || current.error != null) return
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        loadPhotos(JournalLoad.MORE)
    }

    fun retryFriends() {
        if (!_state.value.isLoadingFriends) loadFriends()
    }

    private fun loadPhotos(load: JournalLoad) {
        val userId = userStore.currentUser.value?.id
        if (userId == null) {
            clearSession()
            return
        }

        photosJob?.cancel()
        val filter = _state.value.filter
        if (load == JournalLoad.MORE && !photoLoader.hasMore) return

        _state.update { current ->
            current.copy(
                items = if (load == JournalLoad.INITIAL) emptyList() else current.items,
                isLoading = load == JournalLoad.INITIAL,
                isRefreshing = load == JournalLoad.REFRESH,
                isLoadingMore = load == JournalLoad.MORE,
                hasMore = if (load == JournalLoad.INITIAL) false else current.hasMore,
                error = null,
                loadMoreError = null,
            )
        }
        photosJob = viewModelScope.launch {
            val result = photoLoader.load(filter = filter, loadMore = load == JournalLoad.MORE)
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId || _state.value.filter != filter) {
                return@launch
            }

            if (result.errors.any { isSessionError(it) }) {
                clearSession()
                return@launch
            }
            if (filter is JournalFilter.Friend && result.errors.any { isNotFriends(it) }) {
                returnToAll(filter.id)
                return@launch
            }

            _state.update { current ->
                JournalStateMapper.photosLoaded(
                    current = current,
                    result = result,
                    load = load,
                    user = userStore.currentUser.value,
                )
            }
        }
    }

    private fun loadFriends() {
        val userId = userStore.currentUser.value?.id ?: return
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
                if (reason == FriendError.UNAUTHENTICATED || reason == FriendError.SESSION_CHANGED) {
                    clearSession()
                } else {
                    _state.update {
                        it.copy(isLoadingFriends = false, friendsError = R.string.journal_friends_error)
                    }
                }
            }
        }
    }

    private fun showFriends(profiles: List<PublicProfile>) {
        _state.update { current ->
            JournalStateMapper.friendsLoaded(
                current = current,
                profiles = profiles,
                user = userStore.currentUser.value,
            )
        }
        val filter = _state.value.filter
        if (filter is JournalFilter.Friend && _state.value.friends.none { it.id == filter.id }) {
            returnToAll(filter.id)
        }
    }

    private fun returnToAll(friendId: String) {
        _state.update { it.copy(friends = it.friends.filterNot { owner -> owner.id == friendId }) }
        selectFilter(JournalFilter.All)
        loadFriends()
    }

    private fun isSessionError(error: Throwable): Boolean {
        val reason = (error as? MomentException)?.reason
        return reason == MomentError.UNAUTHENTICATED || reason == MomentError.SESSION_CHANGED
    }

    private fun isNotFriends(error: Throwable): Boolean {
        return (error as? MomentException)?.backendCode == "NOT_FRIENDS"
    }

    private fun restoredFilter(): JournalFilter {
        return when (val value = savedState.get<String>("journal_filter")) {
            null, "all" -> JournalFilter.All
            "me" -> JournalFilter.OnlyMe
            else -> JournalFilter.Friend(value)
        }
    }

    private fun saveFilter() {
        savedState["journal_owner"] = ownerId
        savedState["journal_filter"] = when (val filter = _state.value.filter) {
            JournalFilter.All -> "all"
            JournalFilter.OnlyMe -> "me"
            is JournalFilter.Friend -> filter.id
        }
    }

    private fun clearSession() {
        photosJob?.cancel()
        friendsJob?.cancel()
        ownerId = null
        photoLoader.reset()
        savedState["journal_owner"] = null
        savedState["journal_filter"] = "all"
        _state.value = JournalUiState(error = R.string.journal_session_error)
    }
}
