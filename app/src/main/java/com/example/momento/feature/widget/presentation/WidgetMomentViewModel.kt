package com.example.momento.feature.widget.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.domain.repository.MomentRepository
import com.example.momento.feature.moment.presentation.journal.JournalItem
import com.example.momento.feature.widget.domain.repository.WidgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class WidgetMomentUiState(
    val item: JournalItem? = null,
    val isLoading: Boolean = false,
    val error: Int? = null,
)

@HiltViewModel
class WidgetMomentViewModel @Inject constructor(
    private val moments: MomentRepository,
    private val widgets: WidgetRepository,
    private val userStore: CurrentUserStore,
) : ViewModel() {
    private val sessionVersion = userStore.sessionVersion.value
    private val _state = MutableStateFlow(WidgetMomentUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            userStore.sessionVersion.collect { version ->
                if (version != sessionVersion) {
                    loadJob?.cancel()
                    _state.value = WidgetMomentUiState(error = R.string.journal_session_error)
                }
            }
        }
    }

    fun load(photoId: String) {
        if (_state.value.isLoading) {
            return
        }
        if (userStore.currentUser.value == null || userStore.sessionVersion.value != sessionVersion) {
            _state.value = WidgetMomentUiState(error = R.string.journal_session_error)
            return
        }

        _state.value = WidgetMomentUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            // Fetch this exact photo: it may no longer be the newest photo in the feed.
            val result = moments.getMoment(photoId)
            currentCoroutineContext().ensureActive()
            if (userStore.sessionVersion.value != sessionVersion) {
                return@launch
            }

            val moment = result.getOrNull()
            if (moment == null) {
                _state.value = WidgetMomentUiState(error = errorMessage(result.exceptionOrNull()))
                return@launch
            }

            val snapshot = widgets.data.first().snapshot
            currentCoroutineContext().ensureActive()
            if (userStore.sessionVersion.value != sessionVersion) {
                return@launch
            }
            val friend = snapshot?.friends?.firstOrNull { it.id == moment.ownerId }
            val owner = friend?.let {
                MomentOwner(it.id, it.username, it.displayName, it.avatarUrl)
            }
            _state.value = WidgetMomentUiState(
                // Widget photos belong to friends; reuse the existing read-only detail UI.
                item = JournalItem(moment = moment, owner = owner),
            )
        }
    }

    private fun errorMessage(error: Throwable?): Int {
        val exception = error as? MomentException ?: return R.string.widget_open_error
        return when {
            exception.backendCode == "PHOTO_NOT_FOUND" ||
                exception.backendCode == "PHOTO_ACCESS_DENIED" -> R.string.journal_photo_unavailable
            exception.reason == MomentError.NETWORK -> R.string.journal_network_error
            exception.reason == MomentError.UNAUTHENTICATED ||
                exception.reason == MomentError.SESSION_CHANGED -> R.string.journal_session_error
            else -> R.string.widget_open_error
        }
    }
}
