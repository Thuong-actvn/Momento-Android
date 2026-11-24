package com.example.momento.feature.moment.presentation.journal

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.data.realtime.MomentUpdates
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.MomentReaction
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JournalDetailUiState(
    val item: JournalItem? = null,
    val showReactions: Boolean = false,
    val reactions: List<MomentReaction> = emptyList(),
    val isLoadingReactions: Boolean = false,
    @param:StringRes val reactionsError: Int? = null,
    val showDeleteConfirmation: Boolean = false,
    val isDeleting: Boolean = false,
    @param:StringRes val deleteError: Int? = null,
    val deletedMomentId: String? = null,
)

@HiltViewModel
class JournalDetailViewModel @Inject constructor(
    private val moments: MomentRepository,
    private val userStore: CurrentUserStore,
    private val updates: MomentUpdates,
) : ViewModel() {
    private val _state = MutableStateFlow(JournalDetailUiState())
    val state = _state.asStateFlow()

    private var reactionJob: Job? = null
    private var reactionRefreshRequested = false
    private var reactionsVisible = false
    private var reactionVersion = updates.reactionVersion.value
    private var deleteJob: Job? = null
    private val sessionVersion = userStore.sessionVersion.value

    init {
        viewModelScope.launch {
            userStore.sessionVersion.collect { version ->
                if (version != sessionVersion) {
                    clearSession()
                }
            }
        }
        viewModelScope.launch {
            updates.reactionVersion.collectLatest { version ->
                if (version != reactionVersion) {
                    reactionVersion = version
                    delay(500)
                    loadReactions()
                }
            }
        }
    }

    fun open(item: JournalItem) {
        val userId = userStore.currentUser.value?.id ?: return
        if (_state.value.isDeleting || _state.value.deletedMomentId != null) return
        stopLoadingReactions()
        _state.value = JournalDetailUiState(
            item = item.copy(isOwn = item.moment.ownerId == userId),
        )
        if (reactionsVisible) {
            loadReactions()
        }
    }

    fun close() {
        if (_state.value.isDeleting) return
        reactionsVisible = false
        stopLoadingReactions()
        _state.value = JournalDetailUiState(deletedMomentId = _state.value.deletedMomentId)
    }

    // The route calls this when the detail becomes visible, pauses, or leaves the screen.
    fun setReactionsVisible(visible: Boolean) {
        if (reactionsVisible == visible) return
        reactionsVisible = visible
        if (visible) {
            loadReactions()
        } else {
            stopLoadingReactions()
        }
    }

    fun loadReactions() {
        val item = ownItem() ?: return
        if (!reactionsVisible || _state.value.isDeleting) return
        reactionRefreshRequested = true
        if (_state.value.isLoadingReactions) return
        val photoId = item.moment.id
        val version = userStore.sessionVersion.value

        _state.update {
            it.copy(showReactions = true, isLoadingReactions = true)
        }
        reactionJob = viewModelScope.launch {
            while (reactionRefreshRequested) {
                reactionRefreshRequested = false
                _state.update { it.copy(reactionsError = null) }
                try {
                    updateReactions(photoId, version)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    currentCoroutineContext().ensureActive()
                    if (isViewingReactions(photoId, version)) {
                        _state.update {
                            it.copy(reactionsError = errorMessage(error, R.string.journal_reactions_error))
                        }
                    }
                }
                if (!isViewingReactions(photoId, version)) return@launch
            }
            _state.update { it.copy(isLoadingReactions = false) }
        }
    }

    private suspend fun updateReactions(photoId: String, version: Long) {
        val history = moments.getReactions(photoId).getOrThrow()
        currentCoroutineContext().ensureActive()
        if (!isViewingReactions(photoId, version)) return
        val sortedHistory = history.sortedByDescending { it.createdAt }
        _state.update { it.copy(reactions = sortedHistory) }

        val unseen = moments.viewUnseenReactions(photoId).getOrThrow()
        currentCoroutineContext().ensureActive()
        if (!isViewingReactions(photoId, version)) return
        val combined = (unseen + history)
            .distinctBy { it.id }
            .sortedByDescending { it.createdAt }
        _state.update { it.copy(reactions = combined) }
    }

    private fun isViewingReactions(photoId: String, version: Long): Boolean {
        return reactionsVisible && userStore.sessionVersion.value == version &&
            ownItem()?.moment?.id == photoId && !_state.value.isDeleting
    }

    private fun stopLoadingReactions() {
        reactionJob?.cancel()
        reactionJob = null
        reactionRefreshRequested = false
        _state.update { it.copy(isLoadingReactions = false) }
    }

    fun requestDelete() {
        if (ownItem() == null || _state.value.isDeleting) return
        _state.update { it.copy(showDeleteConfirmation = true, deleteError = null) }
    }

    fun dismissDeleteConfirmation() {
        if (_state.value.isDeleting) return
        _state.update { it.copy(showDeleteConfirmation = false, deleteError = null) }
    }

    fun delete() {
        val item = ownItem() ?: return
        val version = userStore.sessionVersion.value
        val current = _state.value
        if (!current.showDeleteConfirmation || current.isDeleting) return
        val photoId = item.moment.id
        stopLoadingReactions()
        _state.update { it.copy(isDeleting = true, deleteError = null) }

        deleteJob = viewModelScope.launch {
            val result = moments.deleteMoment(photoId)
            currentCoroutineContext().ensureActive()
            if (userStore.sessionVersion.value != version) return@launch

            result.onSuccess {
                _state.value = JournalDetailUiState(deletedMomentId = photoId)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                _state.update {
                    it.copy(
                        isDeleting = false,
                        deleteError = errorMessage(error, R.string.journal_delete_error),
                    )
                }
            }
        }
    }

    fun consumeDeletedMoment(momentId: String) {
        if (_state.value.deletedMomentId == momentId) {
            _state.update { it.copy(deletedMomentId = null) }
        }
    }

    private fun ownItem(): JournalItem? {
        val item = _state.value.item ?: return null
        val userId = userStore.currentUser.value?.id ?: return null
        return item.takeIf { it.moment.ownerId == userId }
    }

    @StringRes
    private fun errorMessage(error: Throwable, @StringRes fallback: Int): Int {
        val exception = error as? MomentException ?: return fallback
        return when (exception.reason) {
            MomentError.NETWORK -> R.string.journal_network_error
            MomentError.SERVER -> R.string.journal_server_error
            MomentError.UNAUTHENTICATED, MomentError.SESSION_CHANGED -> R.string.journal_session_error
            else -> when (exception.backendCode) {
                "PHOTO_NOT_OWNER" -> R.string.journal_owner_required
                "PHOTO_NOT_FOUND", "PHOTO_ACCESS_DENIED" -> R.string.journal_photo_unavailable
                else -> fallback
            }
        }
    }

    private fun clearSession() {
        reactionsVisible = false
        stopLoadingReactions()
        deleteJob?.cancel()
        _state.value = JournalDetailUiState()
    }
}
