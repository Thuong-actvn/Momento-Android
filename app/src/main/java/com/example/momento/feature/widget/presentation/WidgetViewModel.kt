package com.example.momento.feature.widget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.background.BackgroundWorkScheduler
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WidgetUiState(
    val snapshot: WidgetSnapshot? = null,
    val selectedFriendId: String? = null,
    val currentPhotoId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: Int? = null,
    val selectionError: Int? = null,
) {
    val friends: List<WidgetFriend>
        get() = snapshot?.friends.orEmpty()

    val photos: List<WidgetPhoto>
        get() {
            val allPhotos = snapshot?.photos.orEmpty()
            val friendId = selectedFriendId
            if (friendId == null) {
                return allPhotos
            }

            return allPhotos.filter { photo ->
                photo.friendId == friendId
            }
        }

    val photo: WidgetPhoto?
        get() {
            val visiblePhotos = photos
            val selectedPhoto = visiblePhotos.firstOrNull { photo ->
                photo.id == currentPhotoId
            }
            return selectedPhoto ?: visiblePhotos.firstOrNull()
        }

    val sender: WidgetFriend?
        get() {
            val currentPhoto = photo ?: return null
            return friends.firstOrNull { friend -> friend.id == currentPhoto.friendId }
        }

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
    private val workScheduler: BackgroundWorkScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(WidgetUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.data.collect { data ->
                _state.update { current ->
                    val sourceChanged = current.selectedFriendId != data.selectedFriendId
                    val currentPhotoId = if (sourceChanged) null else current.currentPhotoId
                    current.copy(
                        snapshot = data.snapshot,
                        selectedFriendId = data.selectedFriendId,
                        currentPhotoId = currentPhotoId,
                    )
                }
            }
        }
        loadData()
    }

    fun loadData() {
        if (_state.value.isLoading) {
            return
        }

        _state.update { current ->
            current.copy(
                isLoading = true,
                error = null,
            )
        }

        viewModelScope.launch {
            val result = repository.refresh()
            workScheduler.updateWidgets()
            val exception = result.exceptionOrNull()
            val sessionExpired = when (exception) {
                is WidgetSessionChangedException -> true
                is ApiException -> exception.statusCode == 401 || exception.statusCode == 403
                else -> false
            }
            val errorMessage = when {
                exception == null -> null
                sessionExpired -> R.string.widget_session_error
                else -> R.string.widget_load_error
            }

            _state.update { current ->
                current.copy(
                    isLoading = false,
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
            workScheduler.updateWidgets()
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
