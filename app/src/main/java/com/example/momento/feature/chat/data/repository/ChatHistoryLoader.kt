package com.example.momento.feature.chat.data.repository

import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatRoomStore
import com.example.momento.feature.chat.data.local.ChatSyncStateEntity
import com.example.momento.feature.chat.data.mapper.backendUuid
import com.example.momento.feature.chat.data.mapper.toDomain
import com.example.momento.feature.chat.data.remote.ChatApi
import com.example.momento.feature.chat.data.remote.ChatError
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.chat.data.remote.decodeChatResponse
import com.example.momento.feature.chat.data.remote.toChatException
import com.example.momento.feature.chat.domain.model.ChatMessagePage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ChatHistoryLoader @Inject constructor(
    private val api: ChatApi,
    private val store: ChatRoomStore,
    private val users: CurrentUserStore,
) {
    private var trackedGeneration = -1L
    private val peers = mutableMapOf<String, Peer>()

    fun markPeerActive(session: CurrentUserStore.Session, peerId: String) {
        users.withSession(session) {
            val peer = peerForSession(session, peerId)
            peer.seenInForeground = true
        }
    }

    fun startObservingPeer(peerId: String): CurrentUserStore.Session? {
        synchronized(users) {
            val session = users.sessionOrNull() ?: return null
            val peer = peerForSession(session, peerId)
            peer.observerCount++
            return session
        }
    }

    fun stopObservingPeer(session: CurrentUserStore.Session, peerId: String) {
        synchronized(users) {
            if (trackedGeneration != session.generation) {
                return
            }
            val peer = peers[peerId] ?: return
            peer.observerCount--
        }
    }

    fun endForeground(session: CurrentUserStore.Session) {
        synchronized(users) {
            if (trackedGeneration != session.generation) {
                return
            }
            for (peer in peers.values) {
                peer.seenInForeground = false
            }
        }
    }

    fun activePeerIds(session: CurrentUserStore.Session): List<String> {
        synchronized(users) {
            users.checkSession(session)
            if (trackedGeneration != session.generation) {
                return emptyList()
            }
            val activeIds = mutableListOf<String>()
            for ((peerId, peer) in peers) {
                if (peer.seenInForeground || peer.observerCount > 0) {
                    activeIds.add(peerId)
                }
            }
            return activeIds
        }
    }

    suspend fun load(
        session: CurrentUserStore.Session,
        otherUserId: String,
        cursor: String? = null,
        limit: Int = 20,
    ): ChatMessagePage {
        val peerId = backendUuid(otherUserId)
        val accountId = checkNotNull(session.userId)
        require(peerId != accountId) { "Cannot chat with yourself" }
        require(limit in 1..50) { "Message page limit must be between 1 and 50" }
        require(cursor == null || cursor.isNotBlank()) { "Omit cursor on the first page" }

        val requestLock = requestLockForPeer(session, peerId)
        return requestLock.withLock {
            val state = store.syncState(session, peerId)
                ?: ChatSyncStateEntity(
                    accountId = accountId,
                    peerId = peerId,
                    lastSyncedMessageId = null,
                    nextOlderCursor = null,
                    hasMore = false,
                )
            if (cursor == null) {
                refreshLatestMessages(session, state, limit)
            } else {
                loadOlderPage(session, state, cursor, limit)
            }
        }
    }

    private suspend fun loadOlderPage(
        session: CurrentUserStore.Session,
        state: ChatSyncStateEntity,
        cursor: String,
        limit: Int,
    ): ChatMessagePage {
        if (cursor != state.nextOlderCursor) {
            // Another request may have loaded this page while we waited for the lock.
            return ChatMessagePage(
                data = emptyList(),
                nextCursor = state.nextOlderCursor,
                hasMore = state.hasMore,
            )
        }

        val page = fetchPage(state.peerId, cursor, limit)
        val updatedState = state.copy(
            nextOlderCursor = if (page.hasMore) page.nextCursor else null,
            hasMore = page.hasMore,
        )
        store.mergeMessages(session, state.peerId, page.data, updatedState)
        return page
    }

    private suspend fun refreshLatestMessages(
        session: CurrentUserStore.Session,
        state: ChatSyncStateEntity,
        limit: Int,
    ): ChatMessagePage {
        val firstPage = fetchPage(state.peerId, cursor = null, limit = limit)
        val newestMessage = firstPage.data.firstOrNull()
        if (newestMessage == null) {
            return firstPage.copy(nextCursor = state.nextOlderCursor, hasMore = state.hasMore)
        }

        val lastSyncedId = state.lastSyncedMessageId
        if (lastSyncedId == null) {
            val initialState = state.copy(
                lastSyncedMessageId = newestMessage.id,
                nextOlderCursor = if (firstPage.hasMore) firstPage.nextCursor else null,
                hasMore = firstPage.hasMore,
            )
            store.mergeMessages(session, state.peerId, firstPage.data, initialState)
            return firstPage
        }

        var page = firstPage
        val visitedCursors = mutableSetOf<String>()
        while (page.hasMore) {
            val reachedLastSync = page.data.any { message -> message.id == lastSyncedId }
            if (reachedLastSync) {
                break
            }
            val nextCursor = checkNotNull(page.nextCursor)
            if (!visitedCursors.add(nextCursor)) {
                throw ChatException(ChatError.INVALID_RESPONSE)
            }

            // Cache each page now. Advance the sync marker only after the whole refresh succeeds.
            store.mergeMessages(session, state.peerId, page.data)
            users.checkSession(session)
            page = fetchPage(state.peerId, nextCursor, limit)
        }

        val updatedState = if (page.hasMore) {
            state.copy(lastSyncedMessageId = newestMessage.id)
        } else {
            // These pages already cover the full server history.
            state.copy(
                lastSyncedMessageId = newestMessage.id,
                nextOlderCursor = null,
                hasMore = false,
            )
        }
        store.mergeMessages(session, state.peerId, page.data, updatedState)
        return firstPage.copy(nextCursor = updatedState.nextOlderCursor, hasMore = updatedState.hasMore)
    }

    private suspend fun fetchPage(peerId: String, cursor: String?, limit: Int): ChatMessagePage {
        try {
            val response = api.getMessages(peerId, cursor, limit)
            return decodeChatResponse {
                val page = response.requireData().toDomain()
                require(page.data.size <= limit) { "Message page exceeds the requested limit" }
                if (page.hasMore) {
                    require(page.nextCursor != cursor) {
                        "The next cursor must differ from the requested cursor"
                    }
                }
                page
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = error.toChatException()
            if (cursor == null && failure.reason == ChatError.CONVERSATION_NOT_FOUND) {
                return ChatMessagePage(emptyList(), null, false)
            }
            throw failure
        }
    }

    private fun requestLockForPeer(session: CurrentUserStore.Session, peerId: String): Mutex {
        return users.withSession(session) {
            val peer = peerForSession(session, peerId)
            peer.seenInForeground = true
            peer.requestLock
        }
    }

    // All peer tracking is guarded by the same lock as the current session.
    private fun peerForSession(session: CurrentUserStore.Session, peerId: String): Peer {
        if (trackedGeneration != session.generation) {
            peers.clear()
            trackedGeneration = session.generation
        }
        return peers.getOrPut(peerId) { Peer() }
    }

    private class Peer {
        val requestLock = Mutex()
        var seenInForeground = false
        var observerCount = 0
    }
}
