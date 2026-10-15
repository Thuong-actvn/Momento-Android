package com.example.momento.feature.chat.presentation.reply

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.remote.ChatError
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.chat.domain.repository.ChatRepository
import com.example.momento.feature.chat.presentation.detail.CHAT_MESSAGE_PAGE_SIZE
import com.example.momento.feature.chat.presentation.detail.chatHistoryError
import com.example.momento.feature.chat.presentation.detail.chatSendError
import com.example.momento.feature.moment.domain.model.Moment
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

@HiltViewModel
class MomentReplyViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val users: CurrentUserStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(MomentReplyUiState())
    val state = _state.asStateFlow()

    private var session: CurrentUserStore.Session? = null
    private var hasStartedSession = false
    private var selectedMoment: Moment? = null
    private var sendingPhotoId: String? = null
    private var checkingPhotoId: String? = null
    private var sendJob: Job? = null
    private var checkJob: Job? = null
    private val drafts = mutableMapOf<String, String>()
    private val uncertainOwners = mutableMapOf<String, String>()
    private val errors = mutableMapOf<String, Int>()

    init {
        viewModelScope.launch {
            combine(users.currentUser, users.sessionVersion) { _, _ -> currentSession() }
                .distinctUntilChangedBy { it?.generation }
                .collect { onSessionChanged(it) }
        }
    }

    fun selectMoment(moment: Moment?) {
        val currentSession = session ?: return
        withSession(currentSession) {
            selectedMoment = moment
            showSelectedMoment()
        }
    }

    fun onDraftChange(photoId: String, draft: String) {
        val currentSession = session ?: return
        if (_state.value.photoId != photoId || !_state.value.canReply) return
        withSession(currentSession) {
            drafts[photoId] = draft
            if (photoId !in uncertainOwners) errors.remove(photoId)
            saveDrafts()
            showSelectedMoment()
        }
    }

    fun send(photoId: String) {
        val requestSession = session ?: return
        val current = _state.value
        if (current.photoId != photoId || !current.canSend) return
        val ownerId = checkNotNull(current.photoOwnerId)
        val submittedDraft = current.draft

        withSession(requestSession) {
            sendingPhotoId = photoId
            errors.remove(photoId)
            // Lưu trước POST: khôi phục sau gián đoạn phải kiểm tra lịch sử, không tự gửi lại.
            uncertainOwners[photoId] = ownerId
            saveDrafts()
            showSelectedMoment()
            sendJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = chat.replyToMoment(photoId, ownerId, submittedDraft.trim())
                currentCoroutineContext().ensureActive()
                withSession(requestSession) {
                    sendingPhotoId = null
                    val error = result.exceptionOrNull()
                    if (error == null) {
                        uncertainOwners.remove(photoId)
                        if (drafts[photoId] == submittedDraft) drafts.remove(photoId)
                        _state.value = _state.value.copy(notice = MomentReplyNotice(ownerId))
                    } else {
                        if ((error as? ChatException)?.isDeliveryUncertain != true) {
                            uncertainOwners.remove(photoId)
                        }
                        errors[photoId] = replyError(error)
                    }
                    saveDrafts()
                    showSelectedMoment()
                }
            }
        }
    }

    fun checkDelivery(photoId: String) {
        val requestSession = session ?: return
        val ownerId = uncertainOwners[photoId] ?: return
        if (_state.value.photoId != photoId || sendingPhotoId != null || checkingPhotoId != null) return
        withSession(requestSession) {
            checkingPhotoId = photoId
            showSelectedMoment()
            checkJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val result = chat.getMessages(ownerId, limit = CHAT_MESSAGE_PAGE_SIZE)
                currentCoroutineContext().ensureActive()
                withSession(requestSession) {
                    checkingPhotoId = null
                    if (result.isSuccess) {
                        uncertainOwners.remove(photoId)
                        errors.remove(photoId)
                        _state.value = _state.value.copy(conversationToOpen = ownerId)
                    } else {
                        errors[photoId] = chatHistoryError(result.exceptionOrNull()!!)
                    }
                    saveDrafts()
                    showSelectedMoment()
                }
            }
        }
    }

    fun openNotice(notice: MomentReplyNotice) {
        val currentSession = session ?: return
        withSession(currentSession) {
            if (_state.value.notice === notice) {
                _state.value = _state.value.copy(conversationToOpen = notice.otherUserId)
            }
        }
    }

    fun clearNotice(notice: MomentReplyNotice) {
        if (_state.value.notice === notice) _state.value = _state.value.copy(notice = null)
    }

    fun takeConversationToOpen(): String? {
        val currentSession = session ?: return null
        var target: String? = null
        withSession(currentSession) {
            target = _state.value.conversationToOpen
            _state.value = _state.value.copy(conversationToOpen = null)
        }
        return target
    }

    private fun showSelectedMoment() {
        val photoId = selectedMoment?.id
        val uncertain = photoId in uncertainOwners && photoId != sendingPhotoId
        _state.value = _state.value.copy(
            ownUserId = session?.userId,
            sessionVersion = session?.generation,
            photoId = photoId,
            photoOwnerId = selectedMoment?.ownerId,
            draft = drafts[photoId].orEmpty(),
            isSending = sendingPhotoId != null,
            isCheckingDelivery = checkingPhotoId != null,
            isDeliveryUncertain = uncertain,
            error = errors[photoId] ?: if (uncertain) R.string.chat_send_uncertain else null,
        )
    }

    private fun onSessionChanged(currentSession: CurrentUserStore.Session?) {
        sendJob?.cancel()
        checkJob?.cancel()
        sendingPhotoId = null
        checkingPhotoId = null
        selectedMoment = null
        drafts.clear()
        uncertainOwners.clear()
        errors.clear()
        session = currentSession
        _state.value = MomentReplyUiState(
            ownUserId = currentSession?.userId,
            sessionVersion = currentSession?.generation,
        )
        if (currentSession == null) {
            if (hasStartedSession) {
                savedState.remove<String>(OWNER_KEY)
                saveDrafts()
            }
            return
        }
        if (!hasStartedSession && savedState.get<String>(OWNER_KEY) == currentSession.userId) {
            drafts.putAll(savedState.get<HashMap<String, String>>(DRAFTS_KEY).orEmpty())
            uncertainOwners.putAll(savedState.get<HashMap<String, String>>(UNCERTAIN_KEY).orEmpty())
        }
        hasStartedSession = true
        savedState[OWNER_KEY] = currentSession.userId
        saveDrafts()
    }

    private fun saveDrafts() {
        savedState[DRAFTS_KEY] = HashMap(drafts.filterValues { it.isNotEmpty() })
        savedState[UNCERTAIN_KEY] = HashMap(uncertainOwners)
    }

    private fun currentSession(): CurrentUserStore.Session? {
        return try {
            users.requireSession()
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun withSession(currentSession: CurrentUserStore.Session, action: () -> Unit) {
        try {
            users.withSession(currentSession, action)
        } catch (_: IllegalStateException) {
            // Kết quả từ phiên cũ không cập nhật draft hoặc điều hướng của phiên mới.
        }
    }

    private fun replyError(error: Throwable): Int {
        return when ((error as? ChatException)?.reason) {
            ChatError.PHOTO_NOT_FOUND -> R.string.chat_reply_photo_missing
            ChatError.PHOTO_ACCESS_DENIED -> R.string.chat_reply_photo_forbidden
            else -> chatSendError(error)
        }
    }

    private companion object {
        const val OWNER_KEY = "moment_reply_owner"
        const val DRAFTS_KEY = "moment_reply_drafts"
        const val UNCERTAIN_KEY = "moment_reply_uncertain"
    }
}
