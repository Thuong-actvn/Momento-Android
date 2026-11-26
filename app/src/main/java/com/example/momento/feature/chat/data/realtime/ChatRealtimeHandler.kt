package com.example.momento.feature.chat.data.realtime

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatRoomStore
import com.example.momento.feature.chat.data.mapper.toDomain
import com.example.momento.feature.chat.data.remote.dto.MessageResponseDto
import com.example.momento.feature.chat.data.repository.ChatHistoryLoader
import com.example.momento.feature.chat.data.repository.ChatRepositoryImpl
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

interface ChatRealtime {
    suspend fun receiveMessage(session: CurrentUserStore.Session, payload: JsonElement): Boolean
    suspend fun synchronize(session: CurrentUserStore.Session)
    fun endForeground(session: CurrentUserStore.Session)
}

class ChatRealtimeHandler @Inject constructor(
    private val store: ChatRoomStore,
    private val history: ChatHistoryLoader,
    private val repository: ChatRepositoryImpl,
) : ChatRealtime {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun receiveMessage(session: CurrentUserStore.Session, payload: JsonElement): Boolean {
        val response = json.decodeFromJsonElement<MessageResponseDto>(payload)
        val message = response.toDomain()
        if (message.senderId == session.userId) {
            // The sender already received this message in the REST response.
            return false
        }
        history.markPeerActive(session, message.senderId)
        store.mergeMessages(session, message.senderId, listOf(message))
        return true
    }

    override suspend fun synchronize(session: CurrentUserStore.Session) {
        suspendRunCatching {
            repository.loadConversations(session)
        }

        for (peerId in history.activePeerIds(session)) {
            // A failed page will be retried on the next sync. Other chats can still load.
            suspendRunCatching {
                history.load(session, peerId)
            }
        }
    }

    override fun endForeground(session: CurrentUserStore.Session) {
        history.endForeground(session)
    }
}
