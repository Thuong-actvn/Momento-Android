package com.example.momento.feature.chat.data.realtime

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatMemoryStore
import com.example.momento.feature.chat.data.mapper.toDomain
import com.example.momento.feature.chat.data.remote.dto.MessageResponseDto
import com.example.momento.feature.chat.data.repository.ChatRepositoryImpl
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

class ChatRealtimeHandler @Inject constructor(
    private val memory: ChatMemoryStore,
    private val repository: ChatRepositoryImpl,
) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun receiveMessage(session: CurrentUserStore.Session, payload: JsonElement): Boolean {
        val response = json.decodeFromJsonElement<MessageResponseDto>(payload)
        val message = response.toDomain()
        if (message.senderId == session.userId) {
            // The sender already received this message in the REST response.
            return false
        }
        memory.mergeMessages(session, message.senderId, listOf(message))
        return true
    }

    suspend fun synchronize(session: CurrentUserStore.Session) {
        suspendRunCatching {
            repository.loadConversations(session)
        }

        for (peerId in memory.cachedPeerIds(session)) {
            // A failed page will be retried on the next sync. Other chats can still load.
            suspendRunCatching {
                loadMissingMessages(session, peerId)
            }
        }
    }

    private suspend fun loadMissingMessages(
        session: CurrentUserStore.Session,
        peerId: String,
    ) {
        var page = repository.loadMessages(session, peerId, cursor = null, limit = 20)
        val newestId = page.data.firstOrNull()?.id ?: return
        val lastSyncedId = memory.lastSyncedMessageId(session, peerId)
        val visitedCursors = mutableSetOf<String>()

        while (page.hasMore) {
            if (page.data.any { message -> message.id == lastSyncedId }) {
                break
            }

            val nextCursor = page.nextCursor ?: return
            if (nextCursor in visitedCursors) {
                return
            }
            visitedCursors.add(nextCursor)
            page = repository.loadMessages(session, peerId, nextCursor, limit = 20)
        }
        memory.markHistorySynced(session, peerId, newestId)
    }
}
