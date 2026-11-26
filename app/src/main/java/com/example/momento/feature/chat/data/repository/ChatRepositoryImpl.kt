package com.example.momento.feature.chat.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.realtime.RealtimeNetworkMonitor
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatRoomStore
import com.example.momento.feature.chat.data.mapper.backendUuid
import com.example.momento.feature.chat.data.mapper.toDomain
import com.example.momento.feature.chat.data.remote.ChatApi
import com.example.momento.feature.chat.data.remote.ChatError
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.chat.data.remote.decodeChatResponse
import com.example.momento.feature.chat.data.remote.dto.SendChatMessageRequest
import com.example.momento.feature.chat.data.remote.toChatException
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ChatMessagePage
import com.example.momento.feature.chat.domain.model.ChatMessageType
import com.example.momento.feature.chat.domain.model.ConversationSummary
import com.example.momento.feature.chat.domain.repository.ChatRepository
import java.net.ConnectException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val api: ChatApi,
    private val users: CurrentUserStore,
    private val store: ChatRoomStore,
    private val history: ChatHistoryLoader,
    private val network: RealtimeNetworkMonitor,
    private val scope: CoroutineScope,
) : ChatRepository {
    override val conversations = store.conversations

    override fun observeMessages(otherUserId: String): Flow<List<ChatMessage>> = flow {
        val peerId = backendUuid(otherUserId)
        val session = history.startObservingPeer(peerId)
        try {
            emitAll(store.observeMessages(peerId))
        } finally {
            if (session != null) {
                history.stopObservingPeer(session, peerId)
            }
        }
    }

    override fun observeOlderCursor(otherUserId: String): Flow<String?> {
        val peerId = backendUuid(otherUserId)
        return store.observeOlderCursor(peerId)
    }

    override suspend fun getConversations(): Result<List<ConversationSummary>> {
        return requestResult { session ->
            loadConversations(session)
        }
    }

    internal suspend fun loadConversations(session: CurrentUserStore.Session): List<ConversationSummary> {
        users.checkSession(session)
        val response = api.getConversations()
        val conversations = decodeChatResponse {
            response.requireData().map { conversation -> conversation.toDomain() }
        }
        store.mergeConversations(session, conversations)
        return conversations
    }

    override suspend fun getMessages(
        otherUserId: String,
        cursor: String?,
        limit: Int,
    ): Result<ChatMessagePage> {
        return requestResult { session ->
            history.load(session, otherUserId, cursor, limit)
        }
    }

    override suspend fun sendText(otherUserId: String, content: String): Result<ChatMessage> {
        return requestResult(sending = true) { session ->
            val peerId = validatePeer(otherUserId, session)
            val body = messageBody(content)
            checkSendConnection()
            val response = api.sendText(peerId, body)
            val message = decodeChatResponse {
                val sent = response.requireData().toDomain()
                require(sent.senderId == session.userId && sent.type == ChatMessageType.TEXT) {
                    "The backend returned a different sender or message type"
                }
                sent
            }
            saveSentMessage(session, peerId, message)
            message
        }
    }

    override suspend fun replyToMoment(
        photoId: String,
        ownerId: String,
        content: String,
    ): Result<ChatMessage> {
        return requestResult(sending = true) { session ->
            val photo = backendUuid(photoId)
            val owner = validatePeer(ownerId, session)
            val body = messageBody(content)
            checkSendConnection()
            val response = api.replyToMoment(photo, body)
            val message = decodeChatResponse {
                val sent = response.requireData().toDomain()
                require(sent.senderId == session.userId) { "The backend returned a different sender" }
                require(sent.type == ChatMessageType.PHOTO_COMMENT && sent.referencePhotoId == photo) {
                    "The backend returned a different message type or photo"
                }
                sent
            }
            saveSentMessage(session, owner, message)
            message
        }
    }

    private suspend fun saveSentMessage(
        session: CurrentUserStore.Session,
        peerId: String,
        message: ChatMessage,
    ) {
        history.markPeerActive(session, peerId)
        val saved = suspendRunCatching {
            store.mergeMessages(session, peerId, listOf(message))
        }
        if (saved.isSuccess) {
            return
        }

        // The server confirmed the send. Reload the history to recover the local copy.
        checkSession(session)
        scope.launch {
            suspendRunCatching {
                history.load(session, peerId)
            }
        }
    }

    private fun validatePeer(value: String, session: CurrentUserStore.Session): String {
        val peer = backendUuid(value)
        require(peer != session.userId) { "Cannot chat with yourself" }
        return peer
    }

    private fun checkSendConnection() {
        if (!network.hasNetwork()) {
            throw ConnectException("No active network")
        }
    }

    private fun messageBody(content: String): RequestBody {
        val text = content.trim()
        require(text.isNotEmpty() && text.length <= 2000) { "Message content must contain 1..2000 characters" }
        val json = Json.encodeToString(SendChatMessageRequest(text))
        val mediaType = "application/json; charset=utf-8".toMediaType()
        return json.toRequestBody(mediaType).oneShot()
    }

    private suspend fun <T> requestResult(
        sending: Boolean = false,
        block: suspend (CurrentUserStore.Session) -> T,
    ): Result<T> = suspendRunCatching {
        val session = try {
            users.requireSession()
        } catch (error: IllegalStateException) {
            throw ChatException(ChatError.UNAUTHENTICATED, cause = error)
        }
        try {
            runWhileSessionIsActive(session, block)
        } catch (error: CancellationException) {
            // Session invalidation becomes a typed failure; caller cancellation stays cancellation.
            currentCoroutineContext().ensureActive()
            checkSession(session)
            throw error
        } catch (error: Exception) {
            checkSession(session)
            throw error.toChatException(sending)
        }
    }

    private suspend fun <T> runWhileSessionIsActive(
        session: CurrentUserStore.Session,
        block: suspend (CurrentUserStore.Session) -> T,
    ): T = coroutineScope {
        // Start watching for sign-out before the network request starts.
        val request = async(start = CoroutineStart.LAZY) { block(session) }
        val sessionWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            users.sessionVersion.first { version -> version != session.generation }
            request.cancel(CancellationException("Chat session has changed"))
        }
        try {
            val result = request.await()
            checkSession(session)
            result
        } finally {
            sessionWatcher.cancel()
        }
    }

    private fun checkSession(session: CurrentUserStore.Session) {
        try {
            users.checkSession(session)
        } catch (error: IllegalStateException) {
            throw ChatException(ChatError.SESSION_CHANGED, cause = error)
        }
    }
}

// OkHttp must not replay a message after the request body has been transmitted.
private fun RequestBody.oneShot(): RequestBody {
    val original = this
    return object : RequestBody() {
        override fun contentType() = original.contentType()
        override fun contentLength() = original.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = original.writeTo(sink)
    }
}
