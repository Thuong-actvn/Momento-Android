package com.example.momento.feature.chat.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.network.ApiException
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatMemoryStore
import com.example.momento.feature.chat.data.mapper.backendUuid
import com.example.momento.feature.chat.data.mapper.toDomain
import com.example.momento.feature.chat.data.remote.ChatApi
import com.example.momento.feature.chat.data.remote.ChatError
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.chat.data.remote.dto.SendChatMessageRequest
import com.example.momento.feature.chat.data.remote.toChatException
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ChatMessagePage
import com.example.momento.feature.chat.domain.model.ChatMessageType
import com.example.momento.feature.chat.domain.model.ConversationSummary
import com.example.momento.feature.chat.domain.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
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
    private val memory: ChatMemoryStore,
) : ChatRepository {
    override val conversations = memory.conversations

    override fun observeMessages(otherUserId: String) = memory.observeMessages(backendUuid(otherUserId))

    override suspend fun getConversations(): Result<List<ConversationSummary>> = requestResult { session ->
        val response = api.getConversations()
        decodeResponse {
            response.requireData().map { it.toDomain() }.also { memory.mergeConversations(session, it) }
        }
    }

    override suspend fun getMessages(
        otherUserId: String,
        cursor: String?,
        limit: Int,
    ): Result<ChatMessagePage> = requestResult { session ->
        val peerId = validatePeer(otherUserId, session)
        require(limit in 1..50) { "Message page limit must be between 1 and 50" }
        require(cursor == null || cursor.isNotBlank()) { "Omit cursor on the first page" }
        val response = try {
            api.getMessages(peerId, cursor, limit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = error.toChatException()
            if (cursor == null && failure.reason == ChatError.CONVERSATION_NOT_FOUND) {
                return@requestResult ChatMessagePage(emptyList(), null, false)
            }
            throw failure
        }
        try {
            decodeResponse {
                response.requireData().toDomain().also { page ->
                    require(!page.hasMore || page.nextCursor != cursor) { "Message cursor did not advance" }
                    memory.mergeMessages(session, peerId, page.data)
                }
            }
        } catch (error: ChatException) {
            if (cursor == null && error.reason == ChatError.CONVERSATION_NOT_FOUND) {
                ChatMessagePage(emptyList(), null, false)
            } else {
                throw error
            }
        }
    }

    override suspend fun sendText(otherUserId: String, content: String): Result<ChatMessage> =
        requestResult(sending = true) { session ->
            val peerId = validatePeer(otherUserId, session)
            val body = messageBody(content)
            val response = api.sendText(peerId, body)
            decodeResponse {
                response.requireData().toDomain().also { message ->
                    require(message.senderId == session.userId && message.type == ChatMessageType.TEXT) {
                        "The backend returned a different sender or message type"
                    }
                    memory.mergeMessages(session, peerId, listOf(message))
                }
            }
        }

    override suspend fun replyToMoment(
        photoId: String,
        ownerId: String,
        content: String,
    ): Result<ChatMessage> = requestResult(sending = true) { session ->
        val photo = backendUuid(photoId)
        val owner = validatePeer(ownerId, session)
        val response = api.replyToMoment(photo, messageBody(content))
        decodeResponse {
            response.requireData().toDomain().also { message ->
                require(message.senderId == session.userId &&
                    message.type == ChatMessageType.PHOTO_COMMENT && message.referencePhotoId == photo
                ) { "The backend returned a different sender, message type or photo" }
                memory.mergeMessages(session, owner, listOf(message))
            }
        }
    }

    private fun validatePeer(value: String, session: CurrentUserStore.Session): String {
        val peer = backendUuid(value)
        require(peer != session.userId) { "Cannot chat with yourself" }
        return peer
    }

    private fun messageBody(content: String): RequestBody {
        val text = content.trim()
        require(text.isNotEmpty() && text.length <= 2000) { "Message content must contain 1..2000 characters" }
        return Json.encodeToString(SendChatMessageRequest(text))
            .toRequestBody("application/json; charset=utf-8".toMediaType()).oneShot()
    }

    private fun <T> decodeResponse(block: () -> T): T = try {
        block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApiException) {
        throw error.toChatException()
    } catch (error: Exception) {
        throw ChatException(ChatError.INVALID_RESPONSE, cause = error)
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
            coroutineScope {
                val request = async(start = CoroutineStart.LAZY) { block(session) }
                val sessionWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                    users.sessionVersion.first { it != session.generation }
                    request.cancel(CancellationException("Chat session has changed"))
                }
                try {
                    request.await().also { checkSession(session) }
                } finally {
                    sessionWatcher.cancel()
                }
            }
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
