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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
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
)

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val repository: FriendRepository,
    private val userStore: CurrentUserStore,
) : ViewModel() {
    private val _state = MutableStateFlow(FriendUiState())
    val state = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var ownerId = userStore.currentUser.value?.id

    init {
        viewModelScope.launch {
            userStore.currentUser.map { it?.id }.distinctUntilChanged().collect { userId ->
                if (userId != ownerId) {
                    refreshJob?.cancel()
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
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true, error = null) }
        refreshJob = viewModelScope.launch {
            repository.getFriends().fold(
                onSuccess = { friends ->
                    _state.value = FriendUiState(friends = friends, hasLoaded = true)
                },
                onFailure = { error ->
                    val reason = (error as? FriendException)?.reason
                    val sessionLost = reason == FriendError.UNAUTHENTICATED ||
                        reason == FriendError.SESSION_CHANGED
                    val message = when {
                        sessionLost -> R.string.friend_session_error
                        reason == FriendError.NETWORK -> R.string.friend_network_error
                        else -> R.string.friend_load_error
                    }
                    _state.update {
                        // Keep existing data when a refresh fails, except after losing the session.
                        if (sessionLost) FriendUiState(error = message)
                        else it.copy(isRefreshing = false, error = message)
                    }
                },
            )
        }
    }
}
