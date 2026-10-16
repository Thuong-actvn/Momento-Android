package com.example.momento.feature.widget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.network.ApiException
import com.example.momento.feature.widget.data.local.WidgetSessionChangedException
import com.example.momento.feature.widget.domain.model.WidgetFriend
import com.example.momento.feature.widget.domain.model.WidgetPhoto
import com.example.momento.feature.widget.domain.model.WidgetSnapshot
import com.example.momento.feature.widget.domain.repository.WidgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WidgetUiState(
    val snapshot: WidgetSnapshot? = null,
    val selectedFriendId: String? = null,
    val currentPhotoId: String? = null,
    val isRefreshing: Boolean = false,
    val isSaving: Boolean = false,
    val error: Int? = null,
    val selectionError: Int? = null,
) {
    val friends: List<WidgetFriend>
        get() = snapshot?.friends.orEmpty()

    val photos: List<WidgetPhoto>
        get() {
            val allPhotos = snapshot?.photos.orEmpty()
            return allPhotos.filter { photo ->
                selectedFriendId == null || photo.friendId == selectedFriendId
            }
        }

    val photo: WidgetPhoto?
        get() {
            val visiblePhotos = photos
            return visiblePhotos.firstOrNull { photo -> photo.id == currentPhotoId }
                ?: visiblePhotos.firstOrNull()
        }

    val sender: WidgetFriend?
        get() = friends.firstOrNull { friend -> friend.id == photo?.friendId }

    val hasUnavailableFriend: Boolean
        get() {
            if (selectedFriendId == null || snapshot == null) {
                return false
            }

            return friends.none { friend -> friend.id == selectedFriendId }
        }
}

@HiltViewModel
class WidgetViewModel @Inject constructor(
    private val repository: WidgetRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WidgetUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.snapshot,
                repository.selectedFriendId,
            ) { snapshot, selectedFriendId ->
                snapshot to selectedFriendId
            }.collect { (snapshot, selectedFriendId) ->
                _state.update { current ->
                    current.copy(
                        snapshot = snapshot,
                        selectedFriendId = selectedFriendId,
                        currentPhotoId = if (current.selectedFriendId == selectedFriendId) {
                            current.currentPhotoId
                        } else {
                            null
                        },
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (_state.value.isRefreshing) {
            return
        }

        _state.update { current ->
            current.copy(
                isRefreshing = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val result = repository.refresh()
            val exception = result.exceptionOrNull()
            val sessionExpired = exception is WidgetSessionChangedException ||
                (exception is ApiException && exception.statusCode in listOf(401, 403))
            val errorMessage = when {
                exception == null -> null
                sessionExpired -> R.string.widget_session_error
                else -> R.string.widget_load_error
            }

            _state.update { current ->
                current.copy(
                    isRefreshing = false,
                    currentPhotoId = if (result.isSuccess) {
                        null
                    } else {
                        current.currentPhotoId
                    },
                    error = errorMessage,
                )
            }
        }
    }

    fun selectFriend(friendId: String?) {
        val current = _state.value
        if (current.isSaving || current.selectedFriendId == friendId) {
            return
        }

        _state.update { currentState ->
            currentState.copy(
                isSaving = true,
                selectionError = null,
            )
        }

        viewModelScope.launch {
            val result = repository.selectFriend(friendId)
            val errorMessage = if (result.isFailure) {
                R.string.widget_save_error
            } else {
                null
            }

            _state.update { currentState ->
                currentState.copy(
                    isSaving = false,
                    selectionError = errorMessage,
                )
            }
        }
    }

    fun nextPhoto() {
        val current = _state.value
        val photos = current.photos
        if (current.selectedFriendId != null || photos.size < 2) {
            return
        }

        val currentIndex = photos.indexOfFirst { it.id == current.photo?.id }
        val nextIndex = (currentIndex + 1) % photos.size
        val nextPhoto = photos[nextIndex]

        _state.update { currentState ->
            currentState.copy(currentPhotoId = nextPhoto.id)
        }
    }
}
