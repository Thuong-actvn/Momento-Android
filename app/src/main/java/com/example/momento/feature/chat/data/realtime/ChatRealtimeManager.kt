package com.example.momento.feature.chat.data.realtime

import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatMemoryStore
import com.example.momento.feature.chat.data.mapper.toDomain
import com.example.momento.feature.chat.data.remote.dto.ChatRealtimeEventDto
import com.example.momento.feature.chat.data.remote.dto.MessageResponseDto
import com.example.momento.feature.chat.domain.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement

@Singleton
class ChatRealtimeManager @Inject constructor(
    private val socket: ChatSocketClient,
    repository: ChatRepository,
    private val memory: ChatMemoryStore,
    private val users: CurrentUserStore,
    scope: CoroutineScope,
) {
    private val appVisible = MutableStateFlow(false)

    private val catchUp = ChatCatchUp(repository, memory, users)
    private val json = Json {
        ignoreUnknownKeys = true
    }

    init {
        scope.launch {
            observeActiveSession()
        }
    }

    fun setForeground(visible: Boolean) {
        appVisible.value = visible
    }

    private suspend fun observeActiveSession() {
        combine(appVisible, users.currentUser, users.sessionVersion) { visible, _, _ ->
            if (visible) {
                currentSession()
            } else {
                null
            }
        }
            .distinctUntilChangedBy { it?.generation }
            .collectLatest { session ->
                // collectLatest cancels the old socket and REST work before starting another session.
                if (session != null) {
                    runSession(session)
                }
            }
    }

    private suspend fun runSession(session: CurrentUserStore.Session) = coroutineScope {
        // Keep one pending request and run REST synchronization one at a time.
        val syncRequests = Channel<Unit>(Channel.CONFLATED)
        try {
            launch {
                synchronizeOnRequest(session, syncRequests)
            }
            launch {
                requestPeriodicSync(syncRequests)
            }

            syncRequests.trySend(Unit)
            keepSocketConnected(session, syncRequests)
        } finally {
            syncRequests.close()
        }
    }

    private suspend fun synchronizeOnRequest(
        session: CurrentUserStore.Session,
        syncRequests: Channel<Unit>,
    ) {
        for (request in syncRequests) {
            // The backend may publish an event before its database transaction commits.
            delay(SYNC_DELAY_MS)
            try {
                catchUp.synchronize(session)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Try again on the next connection or periodic synchronization.
            }
        }
    }

    private suspend fun requestPeriodicSync(syncRequests: Channel<Unit>) {
        // REST also works while the socket is unavailable and repairs missed events.
        while (currentCoroutineContext().isActive) {
            delay(SYNC_INTERVAL_MS)
            syncRequests.trySend(Unit)
        }
    }

    private suspend fun keepSocketConnected(
        session: CurrentUserStore.Session,
        syncRequests: Channel<Unit>,
    ) {
        while (currentCoroutineContext().isActive) {
            try {
                socket.events().collect { event ->
                    users.checkSession(session)
                    when (event) {
                        ChatSocketEvent.Connected -> {
                            syncRequests.trySend(Unit)
                        }
                        is ChatSocketEvent.Message -> {
                            if (handleServerEvent(session, event.body)) {
                                syncRequests.trySend(Unit)
                            }
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // A connection error follows the same reconnect path as a closed socket.
            }

            delay(RECONNECT_DELAY_MS)
        }
    }

    /** Returns true when a chat message was added to the cache. */
    private fun handleServerEvent(session: CurrentUserStore.Session, body: String): Boolean {
        return try {
            val event = json.decodeFromString<ChatRealtimeEventDto>(body)
            if (event.type != "CHAT_MESSAGE") {
                return false
            }

            val payload = event.payload ?: return false
            val message = json.decodeFromJsonElement<MessageResponseDto>(payload).toDomain()
            // The backend sends CHAT_MESSAGE to the recipient; sends are merged from REST.
            if (message.senderId == session.userId) {
                return false
            }

            memory.mergeMessages(session, message.senderId, listOf(message))
            true
        } catch (_: Exception) {
            // Ignore invalid events and keep receiving other messages.
            false
        }
    }

    private fun currentSession(): CurrentUserStore.Session? {
        return try {
            users.requireSession()
        } catch (_: IllegalStateException) {
            null
        }
    }

    private companion object {
        const val RECONNECT_DELAY_MS = 3_000L
        const val SYNC_INTERVAL_MS = 30_000L
        const val SYNC_DELAY_MS = 500L
    }
}
