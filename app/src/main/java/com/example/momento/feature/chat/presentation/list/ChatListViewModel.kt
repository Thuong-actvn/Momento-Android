package com.example.momento.feature.chat.presentation.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.chat.domain.model.ConversationSummary
import com.example.momento.feature.chat.domain.repository.ChatRepository
import com.example.momento.feature.friend.domain.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val friendRepository: FriendRepository,
    private val currentUserStore: CurrentUserStore,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ChatListUiState(
            isLoadingFriends = true,
            isLoadingConversations = true,
        )
    )
    val state = _state.asStateFlow()

    private var session: CurrentUserStore.Session? = null
    private var hasStartedSession = false
    private var friendProfiles = emptyList<PublicProfile>()
    private var conversationSummaries = emptyList<ConversationSummary>()
    private var friendsJob: Job? = null
    private var conversationsJob: Job? = null
    private var conversationsObserver: Job? = null

    init {
        viewModelScope.launch {
            // Theo dõi cả phiên để nhận biết việc đăng nhập lại cùng một tài khoản.
            combine(currentUserStore.currentUser, currentUserStore.sessionVersion) { _, _ ->
                getCurrentSession()
            }
                .distinctUntilChangedBy { currentSession -> currentSession?.generation }
                .collect { currentSession ->
                    onSessionChanged(currentSession)
                }
        }
    }

    fun onQueryChange(query: String) {
        val currentSession = session ?: return

        updateIfSessionActive(currentSession) {
            savedStateHandle[QUERY_KEY] = query
            _state.update { currentState ->
                currentState.copy(query = query)
            }
        }
    }

    fun refresh() {
        val requestSession = session ?: return
        updateIfSessionActive(requestSession) {
            _state.update { currentState ->
                refreshingState(currentState)
            }
        }

        retryFriends()
        retryConversations()
    }

    fun retryFriends() {
        val requestSession = session ?: return
        if (friendsJob?.isActive == true) {
            return
        }

        updateIfSessionActive(requestSession) {
            _state.update { currentState ->
                friendsLoadingState(currentState)
            }
        }

        friendsJob = viewModelScope.launch {
            val result = friendRepository.getFriends()
            currentCoroutineContext().ensureActive()

            updateIfSessionActive(requestSession) {
                val error = result.exceptionOrNull()

                if (error == null) {
                    friendProfiles = result.getOrThrow()
                    val items = buildItems(requestSession)

                    _state.update { currentState ->
                        friendsLoadedState(currentState, items)
                    }
                } else {
                    _state.update { currentState ->
                        friendsErrorState(currentState, error)
                    }
                }
            }
        }
    }

    fun retryConversations() {
        val requestSession = session ?: return
        if (conversationsJob?.isActive == true) {
            return
        }

        updateIfSessionActive(requestSession) {
            _state.update { currentState ->
                conversationsLoadingState(currentState)
            }
        }

        conversationsJob = viewModelScope.launch {
            val result = chatRepository.getConversations()
            currentCoroutineContext().ensureActive()

            updateIfSessionActive(requestSession) {
                val error = result.exceptionOrNull()

                if (error == null) {
                    // Nội dung danh sách lấy từ Flow đã gộp cache, tránh ghi đè bằng response cũ.
                    _state.update { currentState ->
                        conversationsLoadedState(currentState)
                    }
                } else {
                    _state.update { currentState ->
                        conversationsErrorState(currentState, error)
                    }
                }
            }
        }
    }

    private fun getCurrentSession(): CurrentUserStore.Session? {
        return try {
            currentUserStore.requireSession()
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun onSessionChanged(currentSession: CurrentUserStore.Session?) {
        friendsJob?.cancel()
        conversationsJob?.cancel()
        conversationsObserver?.cancel()

        session = currentSession
        friendProfiles = emptyList()
        conversationSummaries = emptyList()

        if (currentSession == null) {
            if (hasStartedSession) {
                savedStateHandle.remove<String>(OWNER_KEY)
                savedStateHandle.remove<String>(QUERY_KEY)
            }
            _state.value = ChatListUiState(
                conversationsError = R.string.chat_session_error,
            )
            return
        }

        // Chỉ khôi phục query khi tạo ViewModel, và chỉ cho đúng tài khoản.
        val savedOwnerId = savedStateHandle.get<String>(OWNER_KEY)
        val canRestoreQuery = !hasStartedSession && savedOwnerId == currentSession.userId
        val query = if (canRestoreQuery) {
            savedStateHandle.get<String>(QUERY_KEY).orEmpty()
        } else {
            ""
        }

        hasStartedSession = true
        savedStateHandle[OWNER_KEY] = currentSession.userId
        savedStateHandle[QUERY_KEY] = query
        _state.value = ChatListUiState(
            query = query,
            isLoadingFriends = true,
            isLoadingConversations = true,
        )

        observeConversations(currentSession)
        retryFriends()
        retryConversations()
    }

    private fun observeConversations(currentSession: CurrentUserStore.Session) {
        conversationsObserver = viewModelScope.launch {
            chatRepository.conversations.collect { conversations ->
                updateIfSessionActive(currentSession) {
                    conversationSummaries = conversations
                    val items = buildItems(currentSession)
                    _state.update { currentState ->
                        currentState.copy(items = items)
                    }
                }
            }
        }
    }

    private fun buildItems(currentSession: CurrentUserStore.Session): List<ChatListItem> {
        val userId = checkNotNull(currentSession.userId)
        return mergeChatList(
            friends = friendProfiles,
            conversations = conversationSummaries,
            ownUserId = userId,
        )
    }

    private fun updateIfSessionActive(
        requestSession: CurrentUserStore.Session,
        update: () -> Unit,
    ) {
        // Kiểm tra phiên và cập nhật cùng lúc để kết quả cũ không lọt sang tài khoản mới.
        try {
            currentUserStore.withSession(requestSession, update)
        } catch (_: IllegalStateException) {
            // onSessionChanged sẽ xóa dữ liệu cũ và tải lại cho phiên mới.
        }
    }

    private companion object {
        const val OWNER_KEY = "chat_list_owner"
        const val QUERY_KEY = "chat_list_query"
    }
}
