package com.example.momento.feature.chat.presentation.detail

import androidx.annotation.StringRes
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.moment.domain.model.Moment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val CHAT_MESSAGE_PAGE_SIZE = 20
const val CHAT_MESSAGE_MAX_LENGTH = 2000

data class ChatDetailUiState(
    val ownUserId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val moments: Map<String, Moment> = emptyMap(),
    val loadingMomentIds: Set<String> = emptySet(),
    val draft: String = "",
    val isLoadingHistory: Boolean = false,
    val hasLoadedHistory: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasMore: Boolean = false,
    val isSending: Boolean = false,
    val isDeliveryUncertain: Boolean = false,
    val lastSentMessageId: String? = null,
    @param:StringRes val historyError: Int? = null,
    @param:StringRes val olderMessagesError: Int? = null,
    @param:StringRes val sendError: Int? = null,
) {
    val isDraftTooLong: Boolean
        get() = draft.trim().length > CHAT_MESSAGE_MAX_LENGTH

    val canSend: Boolean
        get() = ownUserId != null &&
            draft.isNotBlank() &&
            !isDraftTooLong &&
            !isSending &&
            !isDeliveryUncertain
}

fun chatMessageTimeLabel(
    timestamp: Instant,
    now: Instant = Instant.now(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): String {
    val messageTime = timestamp.atZone(zoneId)
    val today = now.atZone(zoneId).toLocalDate()
    val pattern = when {
        messageTime.toLocalDate() == today -> "HH:mm"
        messageTime.year == today.year -> "d/M HH:mm"
        else -> "d/M/yyyy HH:mm"
    }
    return messageTime.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}
