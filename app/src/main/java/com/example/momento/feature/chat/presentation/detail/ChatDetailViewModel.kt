package com.example.momento.feature.chat.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ChatMessageType
import com.example.momento.feature.chat.domain.repository.ChatRepository
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val currentUserStore: CurrentUserStore,
    private val savedStateHandle: SavedStateHandle,
    private val moments: MomentRepository,
) : ViewModel() {
    private val otherUserId = checkNotNull(savedStateHandle.get<String>("otherUserId"))
    private val _state = MutableStateFlow(ChatDetailUiState(isLoadingHistory = true))
    val state = _state.asStateFlow()

    private var session: CurrentUserStore.Session? = null
    private var hasStartedSession = false
    private var nextOlderCursor: String? = null
    private var historyJob: Job? = null
    private var sendJob: Job? = null
    private var messagesObserver: Job? = null
    private var momentJob: Job? = null
    private val momentLoader = ChatMomentLoader(moments, currentUserStore)
    private val attemptedMomentIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            currentUserStore.observeSession().collectLatest { currentSession ->
                onSessionChanged(currentSession)
                if (currentSession != null) {
                    currentUserStore.completedProfileSession.first { verified ->
                        verified?.generation == currentSession.generation
                    }
                    historyJob?.join()
                    loadHistory(older = false)
                }
            }
        }
    }

    fun onDraftChange(draft: String) {
        val currentSession = session ?: return
        withActiveSession(currentSession) {
            savedStateHandle[DRAFT_KEY] = draft
            _state.update { currentState ->
                currentState.copy(
                    draft = draft,
                    sendError = if (currentState.isDeliveryUncertain) currentState.sendError else null,
                )
            }
        }
    }

    fun refresh() {
        if (historyJob?.isActive == true) return
        momentJob?.cancel()
        momentJob = null
        attemptedMomentIds.clear()
        _state.update { it.copy(loadingMomentIds = emptySet()) }
        loadHistory(older = false, showRefreshIndicator = true)
    }

    fun loadOlder() {
        loadHistory(older = true)
    }

    fun send() {
        val requestSession = session ?: return
        val currentState = _state.value
        if (!currentState.canSend) {
            return
        }
        val submittedDraft = currentState.draft

        withActiveSession(requestSession) {
            _state.update { it.copy(isSending = true, sendError = null) }
            savedStateHandle[UNCERTAIN_KEY] = true

            // Bắt đầu ngay trong kiểm tra phiên để repository chụp đúng phiên của draft.
            sendJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = chatRepository.sendText(otherUserId, submittedDraft.trim())
                currentCoroutineContext().ensureActive()

                withActiveSession(requestSession) {
                    val error = result.exceptionOrNull()
                    if (error == null) {
                        val message = result.getOrThrow()
                        _state.update { state ->
                            val draft = if (state.draft == submittedDraft) "" else state.draft
                            savedStateHandle[DRAFT_KEY] = draft
                            state.copy(
                                draft = draft,
                                isSending = false,
                                lastSentMessageId = message.id,
                            )
                        }
                        savedStateHandle[UNCERTAIN_KEY] = false
                    } else {
                        val uncertain = (error as? ChatException)?.isDeliveryUncertain == true
                        savedStateHandle[UNCERTAIN_KEY] = uncertain
                        _state.update { state ->
                            state.copy(
                                isSending = false,
                                sendError = chatSendError(error),
                                isDeliveryUncertain = uncertain,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadHistory(older: Boolean, showRefreshIndicator: Boolean = false) {
        val requestSession = session ?: return
        val currentState = _state.value
        if (historyJob?.isActive == true || currentState.ownUserId == null) {
            return
        }
        val canLoadOlder = currentState.hasLoadedHistory && currentState.hasMore && nextOlderCursor != null
        if (older && !canLoadOlder) {
            return
        }
        val cursor = if (older) nextOlderCursor else null
        val checkingDelivery = !older && currentState.isDeliveryUncertain

        withActiveSession(requestSession) {
            _state.update { state ->
                state.copy(
                    isLoadingHistory = !older,
                    isLoadingOlder = older,
                    isRefreshing = showRefreshIndicator,
                    historyError = null,
                    olderMessagesError = null,
                )
            }
            historyJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = chatRepository.getMessages(otherUserId, cursor, CHAT_MESSAGE_PAGE_SIZE)
                currentCoroutineContext().ensureActive()

                withActiveSession(requestSession) {
                    val error = result.exceptionOrNull()
                    if (error == null) {
                        onHistoryLoaded(checkingDelivery)
                        loadMomentReferences(requestSession)
                    } else {
                        onHistoryLoadFailed(older, error)
                    }
                }
            }
        }
    }

    private fun onHistoryLoaded(checkingDelivery: Boolean) {
        _state.update { state ->
            val loaded = state.copy(
                isLoadingHistory = false,
                isLoadingOlder = false,
                isRefreshing = false,
                hasLoadedHistory = true,
                olderMessagesError = null,
            )
            if (checkingDelivery) {
                loaded.copy(isDeliveryUncertain = false, sendError = null)
            } else {
                loaded
            }
        }
        if (checkingDelivery) {
            savedStateHandle[UNCERTAIN_KEY] = false
        }
    }

    private fun onHistoryLoadFailed(older: Boolean, error: Throwable) {
        val errorMessage = chatHistoryError(error)
        _state.update { state ->
            state.copy(
                isLoadingHistory = false,
                isLoadingOlder = false,
                isRefreshing = false,
                historyError = if (older) null else errorMessage,
                olderMessagesError = if (older) errorMessage else null,
            )
        }
    }

    private fun onSessionChanged(currentSession: CurrentUserStore.Session?) {
        historyJob?.cancel()
        sendJob?.cancel()
        messagesObserver?.cancel()
        momentJob?.cancel()
        momentJob = null
        attemptedMomentIds.clear()
        nextOlderCursor = null
        session = currentSession

        if (currentSession == null) {
            if (hasStartedSession) {
                savedStateHandle.remove<String>(OWNER_KEY)
                savedStateHandle.remove<String>(DRAFT_KEY)
                savedStateHandle.remove<Boolean>(UNCERTAIN_KEY)
            }
            _state.value = ChatDetailUiState(historyError = R.string.chat_session_error)
            return
        }

        val canRestoreDraft = !hasStartedSession &&
            savedStateHandle.get<String>(OWNER_KEY) == currentSession.userId
        val draft = if (canRestoreDraft) savedStateHandle.get<String>(DRAFT_KEY).orEmpty() else ""
        val uncertain = canRestoreDraft && savedStateHandle.get<Boolean>(UNCERTAIN_KEY) == true
        hasStartedSession = true
        savedStateHandle[OWNER_KEY] = currentSession.userId
        savedStateHandle[DRAFT_KEY] = draft
        savedStateHandle[UNCERTAIN_KEY] = uncertain

        if (!isValidRecipient(currentSession.userId)) {
            _state.value = ChatDetailUiState(historyError = R.string.chat_invalid_recipient)
            return
        }

        val fromCache = currentUserStore.isUsingCachedSession()
        _state.value = ChatDetailUiState(
            ownUserId = currentSession.userId,
            draft = draft,
            isLoadingHistory = !fromCache,
            hasLoadedHistory = fromCache,
            isDeliveryUncertain = uncertain,
            sendError = if (uncertain) R.string.chat_send_uncertain else null,
        )
        observeCachedHistory(currentSession)
    }

    private fun observeCachedHistory(currentSession: CurrentUserStore.Session) {
        val messages = chatRepository.observeMessages(otherUserId)
        val olderCursor = chatRepository.observeOlderCursor(otherUserId)
        val cachedHistory = combine(messages, moments.cachedMoments, olderCursor) { rows, photos, cursor ->
            CachedHistory(messages = rows, moments = photos, olderCursor = cursor)
        }

        messagesObserver = viewModelScope.launch {
            cachedHistory.collect { history ->
                withActiveSession(currentSession) {
                    nextOlderCursor = history.olderCursor
                    _state.update { state ->
                        state.copy(
                            messages = history.messages,
                            moments = history.moments,
                            hasReadCachedHistory = true,
                            hasLoadedHistory = state.hasLoadedHistory || history.messages.isNotEmpty(),
                            hasMore = history.olderCursor != null,
                        )
                    }
                    loadMomentReferences(currentSession)
                }
            }
        }
    }

    private fun loadMomentReferences(requestSession: CurrentUserStore.Session) {
        if (currentUserStore.isUsingCachedSession() || momentJob?.isActive == true) {
            return
        }
        withActiveSession(requestSession) {
            momentJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (true) {
                    try {
                        currentUserStore.checkSession(requestSession)
                    } catch (_: IllegalStateException) {
                        break
                    }
                    val photoIds = pendingMomentIds()
                    if (photoIds.isEmpty()) {
                        break
                    }

                    withActiveSession(requestSession) {
                        attemptedMomentIds.addAll(photoIds)
                        _state.update { state ->
                            val missingPhotos = photoIds - state.moments.keys
                            state.copy(loadingMomentIds = state.loadingMomentIds + missingPhotos)
                        }
                    }
                    momentLoader.load(requestSession, photoIds) { photoId ->
                        withActiveSession(requestSession) {
                            _state.update { it.copy(loadingMomentIds = it.loadingMomentIds - photoId) }
                        }
                    }
                }
            }
        }
    }

    private fun pendingMomentIds(): Set<String> {
        val pending = mutableSetOf<String>()
        for (message in _state.value.messages) {
            val photoId = message.referencePhotoId ?: continue
            if (message.type == ChatMessageType.PHOTO_COMMENT && photoId !in attemptedMomentIds) {
                pending.add(photoId)
            }
        }
        return pending
    }

    private fun isValidRecipient(ownUserId: String?): Boolean {
        return try {
            val canonicalId = UUID.fromString(otherUserId).toString()
            canonicalId.equals(otherUserId, ignoreCase = true) && canonicalId != ownUserId
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun withActiveSession(currentSession: CurrentUserStore.Session, action: () -> Unit) {
        try {
            currentUserStore.withSession(currentSession, action)
        } catch (_: IllegalStateException) {
            // Phiên mới sẽ hủy job và xóa state của phiên cũ.
        }
    }

    private data class CachedHistory(
        val messages: List<ChatMessage>,
        val moments: Map<String, Moment>,
        val olderCursor: String?,
    )

    private companion object {
        const val OWNER_KEY = "chat_detail_owner"
        const val DRAFT_KEY = "chat_detail_draft"
        const val UNCERTAIN_KEY = "chat_detail_uncertain"
    }
}
