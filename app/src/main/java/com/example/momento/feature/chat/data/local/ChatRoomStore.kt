package com.example.momento.feature.chat.data.local

import androidx.room.withTransaction
import com.example.momento.core.database.MomentoDatabase
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ConversationSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ChatRoomStore @Inject constructor(
    private val database: MomentoDatabase,
    private val dao: ChatDao,
    private val users: CurrentUserStore,
) {
    private val writeLock = Mutex()

    val conversations: Flow<List<ConversationSummary>> = observeForAccount(emptyList()) { accountId ->
        dao.observeConversations(accountId).map { conversations ->
            conversations.map { conversation -> conversation.toDomain() }
        }
    }

    fun observeMessages(peerId: String): Flow<List<ChatMessage>> {
        return observeForAccount(emptyList()) { accountId ->
            dao.observeMessages(accountId, peerId).map { messages ->
                messages.map { message -> message.toDomain() }
            }
        }
    }

    fun observeOlderCursor(peerId: String): Flow<String?> {
        return observeForAccount(null) { accountId ->
            dao.observeOlderCursor(accountId, peerId)
        }
    }

    suspend fun syncState(session: CurrentUserStore.Session, peerId: String): ChatSyncStateEntity? {
        users.checkSession(session)
        val accountId = checkNotNull(session.userId)
        val state = dao.getSyncState(accountId, peerId)
        users.checkSession(session)
        return state
    }

    suspend fun mergeConversations(
        session: CurrentUserStore.Session,
        incoming: List<ConversationSummary>,
    ) {
        if (incoming.isEmpty()) {
            return
        }

        writeForSession(session) { accountId ->
            for (summary in incoming) {
                val merged = mergeConversationPreview(accountId, summary)
                dao.upsertConversations(listOf(merged.toEntity(accountId)))
            }
        }
    }

    private suspend fun mergeConversationPreview(
        accountId: String,
        incoming: ConversationSummary,
    ): ConversationSummary {
        val cached = findConversation(accountId, incoming.otherUserId, incoming.conversationId)
            ?: return incoming
        val latestMessage = dao.getLatestMessage(accountId, incoming.conversationId)
        val waitingForTimestamp = latestMessage != null && latestMessage.createdAtEpochSecond == null
        val cachedTime = cached.lastMessageAt
        val serverTime = incoming.lastMessageAt
        val keepCachedPreview = cachedTime != null && (serverTime == null || cachedTime >= serverTime)

        if (waitingForTimestamp || keepCachedPreview) {
            return incoming.copy(
                lastMessageContent = cached.lastMessageContent,
                lastMessageAt = cachedTime,
            )
        }
        return incoming
    }

    suspend fun mergeMessages(
        session: CurrentUserStore.Session,
        peerId: String,
        incoming: List<ChatMessage>,
        syncState: ChatSyncStateEntity? = null,
    ) {
        if (incoming.isEmpty() && syncState == null) {
            return
        }

        writeForSession(session) { accountId ->
            if (incoming.isNotEmpty()) {
                val conversation = getOrCreateConversation(accountId, peerId, incoming)
                saveMessages(accountId, conversation, incoming)
                updatePreviewFromLatestMessage(accountId, conversation)
            }
            if (syncState != null) {
                require(syncState.accountId == accountId && syncState.peerId == peerId)
                dao.upsertSyncState(syncState)
            }
        }
    }

    suspend fun clearActiveAccount() {
        writeLock.withLock {
            val accountId = users.clear() ?: return
            database.withTransaction {
                dao.deleteConversations(accountId)
                dao.deleteSyncState(accountId)
            }
        }
    }

    private suspend fun getOrCreateConversation(
        accountId: String,
        peerId: String,
        incoming: List<ChatMessage>,
    ): ConversationSummary {
        val conversationId = incoming.first().conversationId
        val cached = findConversation(accountId, peerId, conversationId)
        if (cached != null) {
            return cached
        }

        val messageFromPeer = incoming.firstOrNull { message -> message.senderId == peerId }
        val conversation = ConversationSummary(
            conversationId = conversationId,
            otherUserId = peerId,
            otherUserDisplayName = messageFromPeer?.senderDisplayName,
            otherUserAvatarUrl = null,
            lastMessageContent = null,
            lastMessageAt = null,
        )
        dao.upsertConversations(listOf(conversation.toEntity(accountId)))
        return conversation
    }

    private suspend fun saveMessages(
        accountId: String,
        conversation: ConversationSummary,
        incoming: List<ChatMessage>,
    ) {
        val conversationId = conversation.conversationId
        val messageIds = incoming.map { message -> message.id }
        val messagesById = mutableMapOf<String, ChatMessageEntity>()
        for (cached in dao.getMessagesById(accountId, messageIds)) {
            messagesById[cached.messageId] = cached
        }
        var lastArrivalOrder = dao.getLatestArrivalOrder(accountId, conversationId)

        for (message in incoming) {
            val knownSender = message.senderId == accountId || message.senderId == conversation.otherUserId
            require(message.conversationId == conversationId && knownSender) {
                "Messages must belong to the requested conversation and participants"
            }
            val previous = messagesById[message.id]
            require(previous == null || previous.conversationId == conversationId) {
                "Message belongs to a different conversation"
            }
            val entity = if (previous == null) {
                lastArrivalOrder++
                message.toEntity(accountId, lastArrivalOrder)
            } else {
                val timestamp = message.createdAt ?: previous.toDomain().createdAt
                message.copy(createdAt = timestamp).toEntity(accountId, previous.arrivalOrder)
            }
            messagesById[message.id] = entity
        }
        dao.upsertMessages(messagesById.values.toList())
    }

    private suspend fun updatePreviewFromLatestMessage(accountId: String, conversation: ConversationSummary) {
        val row = checkNotNull(dao.getLatestMessage(accountId, conversation.conversationId))
        val latest = row.toDomain()
        val previewTime = conversation.lastMessageAt
        if (latest.createdAt != null && previewTime != null && latest.createdAt < previewTime) {
            return
        }
        val updated = conversation.copy(
            lastMessageContent = latest.content,
            lastMessageAt = latest.createdAt,
        )
        dao.upsertConversations(listOf(updated.toEntity(accountId)))
    }

    private suspend fun findConversation(
        accountId: String,
        peerId: String,
        conversationId: String,
    ): ConversationSummary? {
        require(accountId != peerId) { "Cannot chat with yourself" }
        val previous = dao.getConversation(accountId, peerId)
        require(previous == null || previous.conversationId == conversationId) {
            "The peer already belongs to a different conversation"
        }
        val existingPeer = dao.getPeerId(accountId, conversationId)
        require(existingPeer == null || existingPeer == peerId) { "Conversation belongs to a different peer" }
        return previous?.toDomain()
    }

    private suspend fun writeForSession(session: CurrentUserStore.Session, block: suspend (String) -> Unit) {
        writeLock.withLock {
            database.withTransaction {
                users.checkSession(session)
                val accountId = checkNotNull(session.userId)
                block(accountId)
                users.checkSession(session)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <T> observeForAccount(empty: T, observe: (String) -> Flow<T>): Flow<T> {
        return users.observeSession().flatMapLatest { session ->
            val accountId = session?.userId
            if (accountId == null) {
                flowOf(empty)
            } else {
                observe(accountId).map { value ->
                    if (users.sessionOrNull()?.generation == session.generation) {
                        value
                    } else {
                        empty
                    }
                }
            }
        }.distinctUntilChanged()
    }
}
