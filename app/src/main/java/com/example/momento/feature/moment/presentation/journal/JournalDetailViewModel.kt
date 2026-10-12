package com.example.momento.feature.moment.presentation.journal

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.MomentReaction
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
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
) : ViewModel() {
    private val _state = MutableStateFlow(JournalDetailUiState())
    val state = _state.asStateFlow()

    // The backend returns unseen reactions only. Keep viewed results until the account changes.
    private val reactionCache = mutableMapOf<String, List<MomentReaction>>()
    private val reactionJobs = mutableMapOf<String, Job>()
    private var deleteJob: Job? = null
    private var ownerId = userStore.currentUser.value?.id

    init {
        viewModelScope.launch {
            userStore.currentUser.collect { user ->
                if (user?.id != ownerId) {
                    clearSession()
                    ownerId = user?.id
                }
            }
        }
    }

    fun open(item: JournalItem) {
        val userId = userStore.currentUser.value?.id ?: return
        if (_state.value.isDeleting || _state.value.deletedMomentId != null) return
        val photoId = item.moment.id
        _state.value = JournalDetailUiState(
            item = item.copy(isOwn = item.moment.ownerId == userId),
            showReactions = reactionCache.containsKey(photoId) || reactionJobs[photoId]?.isActive == true,
            reactions = reactionCache[photoId].orEmpty(),
            isLoadingReactions = reactionJobs[photoId]?.isActive == true,
        )
        if (item.moment.ownerId == userId && !reactionCache.containsKey(photoId)) {
            loadReactions()
        }
    }

    fun close() {
        if (_state.value.isDeleting) return
        // Let reaction requests finish: cancelling cannot undo the backend's seen flag.
        _state.value = JournalDetailUiState(deletedMomentId = _state.value.deletedMomentId)
    }

    fun loadReactions() {
        val item = ownItem() ?: return
        val userId = userStore.currentUser.value?.id ?: return
        val photoId = item.moment.id
        if (reactionJobs[photoId]?.isActive == true || _state.value.isDeleting) return

        _state.update {
            it.copy(showReactions = true, isLoadingReactions = true, reactionsError = null)
        }
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = moments.viewUnseenReactions(photoId)
                currentCoroutineContext().ensureActive()
                if (userStore.currentUser.value?.id != userId) return@launch

                result.onSuccess { received ->
                    val combined = (reactionCache[photoId].orEmpty() + received)
                        .distinctBy { it.id }
                        .sortedByDescending { it.createdAt }
                    reactionCache[photoId] = combined
                    if (_state.value.item?.moment?.id == photoId) {
                        _state.update {
                            it.copy(reactions = combined, isLoadingReactions = false)
                        }
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    if (_state.value.item?.moment?.id == photoId) {
                        _state.update {
                            it.copy(
                                isLoadingReactions = false,
                                reactionsError = errorMessage(error, R.string.journal_reactions_error),
                            )
                        }
                    }
                }
            } finally {
                reactionJobs.remove(photoId, currentCoroutineContext().job)
            }
        }
        reactionJobs[photoId] = job
        job.start()
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
        val userId = userStore.currentUser.value?.id ?: return
        val current = _state.value
        if (!current.showDeleteConfirmation || current.isDeleting) return
        val photoId = item.moment.id
        _state.update { it.copy(isDeleting = true, deleteError = null) }

        deleteJob = viewModelScope.launch {
            val result = moments.deleteMoment(photoId)
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId) return@launch

            result.onSuccess {
                reactionJobs.remove(photoId)?.cancel()
                reactionCache.remove(photoId)
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
        reactionJobs.values.toList().forEach { it.cancel() }
        reactionJobs.clear()
        reactionCache.clear()
        deleteJob?.cancel()
        _state.value = JournalDetailUiState()
    }
}
