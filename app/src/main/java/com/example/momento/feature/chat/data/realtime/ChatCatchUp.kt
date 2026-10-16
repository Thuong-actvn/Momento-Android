package com.example.momento.feature.chat.data.realtime

import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.local.ChatMemoryStore
import com.example.momento.feature.chat.domain.repository.ChatRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Loads newer messages through REST and keeps the old checkpoint until every missing page succeeds. */
class ChatCatchUp(
    private val repository: ChatRepository,
    private val memory: ChatMemoryStore,
    private val users: CurrentUserStore,
) {
    private var sessionGeneration = -1L
    private val lastSyncedMessageIds = mutableMapOf<String, String?>()

    // The manager runs one synchronization at a time, so checkpoints need no separate lock.
    suspend fun synchronize(session: CurrentUserStore.Session) {
        // Capture checkpoints before REST or socket arrivals can change the cache.
        val checkpoints = snapshotCheckpoints(session)
        requestForSession(session) {
            repository.getConversations()
        }

        for ((otherUserId, lastMessageId) in checkpoints) {
            synchronizeConversation(session, otherUserId, lastMessageId)
        }
    }

    private fun snapshotCheckpoints(session: CurrentUserStore.Session): Map<String, String?> {
        val cachedMessageIds = memory.latestMessageIds(session)
        if (sessionGeneration != session.generation) {
            sessionGeneration = session.generation
            lastSyncedMessageIds.clear()
        }

        for ((otherUserId, messageId) in cachedMessageIds) {
            // null means this history has never been loaded; preserve it across failed attempts.
            if (!lastSyncedMessageIds.containsKey(otherUserId)) {
                lastSyncedMessageIds[otherUserId] = messageId
            }
        }
        return lastSyncedMessageIds.toMap()
    }

    private suspend fun synchronizeConversation(
        session: CurrentUserStore.Session,
        otherUserId: String,
        lastSyncedMessageId: String?,
    ) {
        try {
            var cursor: String? = null
            var newestMessageId: String? = null
            val visitedCursors = mutableSetOf<String>()

            while (true) {
                val page = requestForSession(session) {
                    repository.getMessages(otherUserId, cursor, MESSAGE_PAGE_SIZE)
                }.getOrThrow()

                if (cursor == null) {
                    newestMessageId = page.data.firstOrNull()?.id
                }

                val isFirstLoad = lastSyncedMessageId == null
                val reachedCheckpoint = page.data.any { it.id == lastSyncedMessageId }
                if (isFirstLoad || reachedCheckpoint || !page.hasMore) {
                    users.withSession(session) {
                        lastSyncedMessageIds[otherUserId] = newestMessageId ?: lastSyncedMessageId
                    }
                    return
                }

                val nextCursor = page.nextCursor ?: return
                if (nextCursor == cursor || nextCursor in visitedCursors) {
                    return
                }
                visitedCursors.add(nextCursor)
                cursor = nextCursor
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Pages already merged are useful, but must not become the next retry's checkpoint.
        }
    }

    private suspend fun <T> requestForSession(
        session: CurrentUserStore.Session,
        block: suspend () -> Result<T>,
    ): Result<T> = coroutineScope {
        // Start immediately under the session lock so the repository captures this account.
        val request = users.withSession(session) {
            async(start = CoroutineStart.UNDISPATCHED) {
                block()
            }
        }
        request.await()
    }

    private companion object {
        const val MESSAGE_PAGE_SIZE = 20
    }
}
