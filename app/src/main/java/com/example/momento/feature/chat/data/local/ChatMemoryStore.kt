package com.example.momento.feature.chat.data.local

import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ConversationSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Session-owned cache shared by REST pages and realtime events. */
@Singleton
class ChatMemoryStore @Inject constructor(
    private val users: CurrentUserStore,
    scope: CoroutineScope,
) {
    private data class Snapshot(
        val version: Long,
        val conversations: Map<String, ConversationSummary> = emptyMap(),
        val messages: Map<String, Map<String, ChatMessage>> = emptyMap(),
    )

    private val snapshots = MutableStateFlow(Snapshot(users.sessionVersion.value))

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            users.sessionVersion.collect {
                // Use the same monitor as withSession/clear: a delayed cleanup cannot erase
                // messages that have already been published by the new session.
                synchronized(users) {
                    val version = users.sessionVersion.value
                    if (snapshots.value.version != version) snapshots.value = Snapshot(version)
                }
            }
        }
    }

    // Mask old data as soon as session invalidation is observed, even before cleanup runs.
    private val currentSnapshots = combine(users.sessionVersion, snapshots) { version, snapshot ->
        if (snapshot.version == version) snapshot else Snapshot(version)
    }

    val conversations: Flow<List<ConversationSummary>> = currentSnapshots.map { snapshot ->
        snapshot.conversations.values.sortedWith(
            compareByDescending<ConversationSummary> { it.lastMessageAt }.thenBy { it.otherUserId },
        )
    }.distinctUntilChanged()

    fun observeMessages(otherUserId: String): Flow<List<ChatMessage>> = currentSnapshots.map { snapshot ->
        snapshot.messages[otherUserId].orEmpty().values.sortedWith(messageOrder)
    }.distinctUntilChanged()

    fun rememberPeer(session: CurrentUserStore.Session, otherUserId: String) {
        mutate(session) { snapshot ->
            require(otherUserId != session.userId) { "Cannot chat with yourself" }
            if (otherUserId in snapshot.messages) {
                return@mutate snapshot
            }

            val messages = snapshot.messages + (otherUserId to emptyMap<String, ChatMessage>())
            snapshot.copy(messages = messages)
        }
    }

    /** Take this snapshot before REST starts so new socket messages cannot hide a gap. */
    fun latestMessageIds(session: CurrentUserStore.Session): Map<String, String?> {
        return users.withSession(session) {
            val snapshot = snapshots.value
            if (snapshot.version != session.generation) {
                return@withSession emptyMap()
            }

            snapshot.messages.mapValues { (_, messages) ->
                val orderedMessages = messages.values.sortedWith(messageOrder)
                val latestDatedMessage = orderedMessages.lastOrNull { it.createdAt != null }
                val firstPendingMessage = orderedMessages.firstOrNull()
                latestDatedMessage?.id ?: firstPendingMessage?.id
            }
        }
    }

    fun mergeConversations(
        session: CurrentUserStore.Session,
        incoming: List<ConversationSummary>,
    ) = mutate(session) { snapshot ->
        val merged = snapshot.conversations.toMutableMap()
        for (summary in incoming) {
            require(summary.otherUserId != session.userId) { "A conversation must have another user" }
            require(merged.values.none {
                it.conversationId == summary.conversationId && it.otherUserId != summary.otherUserId
            }) { "A conversation cannot belong to multiple peers" }
            val previous = merged[summary.otherUserId]
            require(previous == null || previous.conversationId == summary.conversationId) {
                "The backend returned multiple conversations for the same peer"
            }
            // A list request may have started before a send/event committed. Preserve its preview.
            val awaitingTimestamp = snapshot.messages[summary.otherUserId].orEmpty().values
                .any { it.createdAt == null }
            merged[summary.otherUserId] = if (previous != null &&
                (awaitingTimestamp || (previous.lastMessageAt != null &&
                    (summary.lastMessageAt == null || !summary.lastMessageAt.isAfter(previous.lastMessageAt))))
            ) {
                summary.copy(
                    lastMessageContent = previous.lastMessageContent,
                    lastMessageAt = previous.lastMessageAt,
                )
            } else {
                summary
            }
        }
        snapshot.copy(conversations = merged.toMap())
    }

    /** Pass the session captured before connecting/requesting, never a freshly acquired session. */
    fun mergeMessages(
        session: CurrentUserStore.Session,
        otherUserId: String,
        incoming: List<ChatMessage>,
    ) = mutate(session) { snapshot ->
        require(otherUserId != session.userId) { "Cannot chat with yourself" }
        if (incoming.isEmpty()) return@mutate snapshot
        val conversationId = incoming.first().conversationId
        val previous = snapshot.conversations[otherUserId]
        require(previous == null || previous.conversationId == conversationId) {
            "Messages belong to a different conversation"
        }
        require(snapshot.conversations.values.none {
            it.conversationId == conversationId && it.otherUserId != otherUserId
        }) { "Messages belong to a different peer" }
        require(incoming.all {
            it.conversationId == conversationId && it.senderId in setOf(session.userId, otherUserId)
        }) { "Messages must belong to the requested conversation and participants" }

        val messages = snapshot.messages[otherUserId].orEmpty().toMutableMap()
        incoming.forEach { message ->
            // A delayed send/event response must not erase a timestamp already loaded from REST.
            messages[message.id] = if (message.createdAt == null) {
                message.copy(createdAt = messages[message.id]?.createdAt)
            } else {
                message
            }
        }
        val latest = messages.values.sortedWith(messageOrder).last()
        val summary = previous ?: ConversationSummary(
            conversationId, otherUserId,
            otherUserDisplayName = messages.values
                .filter { it.senderId == otherUserId }.sortedWith(messageOrder).lastOrNull()?.senderDisplayName,
            otherUserAvatarUrl = null, lastMessageContent = null, lastMessageAt = null,
        )
        val updated = if (latest.createdAt == null || summary.lastMessageAt == null ||
            !latest.createdAt.isBefore(summary.lastMessageAt)
        ) {
            summary.copy(lastMessageContent = latest.content, lastMessageAt = latest.createdAt)
        } else {
            summary
        }
        snapshot.copy(
            conversations = snapshot.conversations + (otherUserId to updated),
            messages = snapshot.messages + (otherUserId to messages.toMap()),
        )
    }

    private fun mutate(session: CurrentUserStore.Session, block: (Snapshot) -> Snapshot) {
        users.withSession(session) {
            val current = snapshots.value.takeIf { it.version == session.generation }
                ?: Snapshot(session.generation)
            snapshots.value = block(current)
        }
    }

    private companion object {
        // Unknown timestamps stay after dated messages, in arrival order, until REST fills them.
        val messageOrder = Comparator<ChatMessage> { first, second ->
            when {
                first.createdAt == null -> if (second.createdAt == null) 0 else 1
                second.createdAt == null -> -1
                else -> compareValuesBy(first, second, { it.createdAt }, { it.id })
            }
        }
    }
}
