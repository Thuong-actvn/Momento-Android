package com.example.momento.feature.chat.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.chat.domain.repository.ChatRepository
import com.example.momento.feature.chat.domain.model.ChatMessageType
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
    private var nextCursor: String? = null
    private var historyJob: Job? = null
    private var sendJob: Job? = null
    private var messagesObserver: Job? = null
    private var momentJob: Job? = null
    private val momentLoader = ChatMomentLoader(moments, currentUserStore)
    private val attemptedMomentIds = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            combine(currentUserStore.currentUser, currentUserStore.sessionVersion) { _, _ ->
                getCurrentSession()
            }
                .distinctUntilChangedBy { currentSession -> currentSession?.generation }
                .collect { currentSession ->
                    onSessionChanged(currentSession)
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
        loadHistory(older = false)
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

    private fun loadHistory(older: Boolean) {
        val requestSession = session ?: return
        val currentState = _state.value
        if (historyJob?.isActive == true || currentState.ownUserId == null) {
            return
        }
        if (older && (!currentState.hasLoadedHistory || !currentState.hasMore || nextCursor == null)) {
            return
        }
        val cursor = if (older) nextCursor else null
        val checkingDelivery = !older && currentState.isDeliveryUncertain

        withActiveSession(requestSession) {
            _state.update { state ->
                if (older) {
                    state.copy(isLoadingOlder = true, olderMessagesError = null)
                } else {
                    state.copy(isLoadingHistory = true, historyError = null)
                }
            }
            historyJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = chatRepository.getMessages(otherUserId, cursor, CHAT_MESSAGE_PAGE_SIZE)
                currentCoroutineContext().ensureActive()

                withActiveSession(requestSession) {
                    val error = result.exceptionOrNull()
                    if (error == null) {
                        val page = result.getOrThrow()
                        nextCursor = if (page.hasMore) page.nextCursor else null
                        // Tin nhắn lấy từ cache Flow; response ở đây chỉ cập nhật phân trang.
                        _state.update { state ->
                            state.copy(
                                isLoadingHistory = false,
                                isLoadingOlder = false,
                                hasLoadedHistory = true,
                                hasMore = page.hasMore,
                                olderMessagesError = null,
                                isDeliveryUncertain = if (checkingDelivery) false else state.isDeliveryUncertain,
                                sendError = if (checkingDelivery) null else state.sendError,
                            )
                        }
                        if (checkingDelivery) {
                            savedStateHandle[UNCERTAIN_KEY] = false
                        }
                        loadMomentReferences(requestSession)
                    } else {
                        _state.update { state ->
                            if (older) {
                                state.copy(isLoadingOlder = false, olderMessagesError = chatHistoryError(error))
                            } else {
                                state.copy(isLoadingHistory = false, historyError = chatHistoryError(error))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onSessionChanged(currentSession: CurrentUserStore.Session?) {
        historyJob?.cancel()
        sendJob?.cancel()
        messagesObserver?.cancel()
        momentJob?.cancel()
        momentJob = null
        attemptedMomentIds.clear()
        nextCursor = null
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

        _state.value = ChatDetailUiState(
            ownUserId = currentSession.userId,
            draft = draft,
            isLoadingHistory = true,
            isDeliveryUncertain = uncertain,
            sendError = if (uncertain) R.string.chat_send_uncertain else null,
        )
        messagesObserver = viewModelScope.launch {
            combine(chatRepository.observeMessages(otherUserId), moments.cachedMoments) { messages, photos ->
                messages to photos
            }.collect { (messages, photos) ->
                withActiveSession(currentSession) {
                    _state.update { it.copy(messages = messages, moments = photos) }
                    loadMomentReferences(currentSession)
                }
            }
        }
        loadHistory(older = false)
    }

    private fun loadMomentReferences(requestSession: CurrentUserStore.Session) {
        if (momentJob?.isActive == true) return
        withActiveSession(requestSession) {
            momentJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (true) {
                    try {
                        currentUserStore.checkSession(requestSession)
                    } catch (_: IllegalStateException) {
                        break
                    }
                    val photoIds = _state.value.messages
                        .filter { it.type == ChatMessageType.PHOTO_COMMENT }
                        .mapNotNull { it.referencePhotoId }
                        .filterNot { it in attemptedMomentIds }
                        .toSet()
                    if (photoIds.isEmpty()) break

                    withActiveSession(requestSession) {
                        attemptedMomentIds.addAll(photoIds)
                        _state.update { state ->
                            state.copy(loadingMomentIds = state.loadingMomentIds + (photoIds - state.moments.keys))
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

    private fun isValidRecipient(ownUserId: String?): Boolean {
        return try {
            val canonicalId = UUID.fromString(otherUserId).toString()
            canonicalId.equals(otherUserId, ignoreCase = true) && canonicalId != ownUserId
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun getCurrentSession(): CurrentUserStore.Session? {
        return try {
            currentUserStore.requireSession()
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun withActiveSession(currentSession: CurrentUserStore.Session, action: () -> Unit) {
        try {
            currentUserStore.withSession(currentSession, action)
        } catch (_: IllegalStateException) {
            // Phiên mới sẽ hủy job và xóa state của phiên cũ.
        }
    }

    private companion object {
        const val OWNER_KEY = "chat_detail_owner"
        const val DRAFT_KEY = "chat_detail_draft"
        const val UNCERTAIN_KEY = "chat_detail_uncertain"
    }
}
