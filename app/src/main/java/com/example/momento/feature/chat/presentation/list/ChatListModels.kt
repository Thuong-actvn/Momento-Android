package com.example.momento.feature.chat.presentation.list

import androidx.annotation.StringRes
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.chat.domain.model.ConversationSummary
import java.text.Collator
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ChatListItem(
    val otherUserId: String,
    val conversationId: String? = null,
    val displayName: String? = null,
    val username: String? = null,
    val avatarUrl: String? = null,
    val lastMessageContent: String? = null,
    val lastMessageAt: Instant? = null,
) {
    val hasMessage: Boolean
        get() = lastMessageContent != null
}

data class ChatListUiState(
    val items: List<ChatListItem> = emptyList(),
    val query: String = "",
    val isLoadingFriends: Boolean = false,
    val isLoadingConversations: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasLoadedFriends: Boolean = false,
    val hasLoadedConversations: Boolean = false,
    @param:StringRes val friendsError: Int? = null,
    @param:StringRes val conversationsError: Int? = null,
) {
    val isLoading: Boolean
        get() = isLoadingFriends || isLoadingConversations

    val hasLoaded: Boolean
        get() = hasLoadedFriends || hasLoadedConversations

    val visibleItems: List<ChatListItem>
        get() {
            val searchText = query.trim().removePrefix("@")
            val normalizedQuery = normalizedSearch(searchText)

            if (normalizedQuery.isEmpty()) {
                return items
            }

            return items.filter { item ->
                val nameAndUsername = "${item.displayName.orEmpty()} ${item.username.orEmpty()}"
                val searchableText = normalizedSearch(nameAndUsername)
                searchableText.contains(normalizedQuery)
            }
        }
}

fun mergeChatList(
    friends: List<PublicProfile>,
    conversations: List<ConversationSummary>,
    ownUserId: String,
): List<ChatListItem> {
    val friendsById = friends.associateBy { friend -> friend.id }
    val conversationsByUserId = conversations.associateBy { conversation -> conversation.otherUserId }
    val allUserIds = friendsById.keys + conversationsByUserId.keys
    val items = mutableListOf<ChatListItem>()

    for (userId in allUserIds) {
        if (userId == ownUserId) {
            continue
        }

        val friend = friendsById[userId]
        val conversation = conversationsByUserId[userId]

        val displayName: String?
        val avatarUrl: String?
        if (friend != null) {
            displayName = friend.displayName
            avatarUrl = friend.avatarUrl
        } else {
            displayName = conversation?.otherUserDisplayName
            avatarUrl = conversation?.otherUserAvatarUrl
        }

        val item = ChatListItem(
            otherUserId = userId,
            conversationId = conversation?.conversationId,
            displayName = cleanName(displayName),
            username = cleanName(friend?.username),
            avatarUrl = avatarUrl,
            lastMessageContent = conversation?.lastMessageContent,
            lastMessageAt = conversation?.lastMessageAt,
        )
        items.add(item)
    }

    val vietnameseNames = Collator.getInstance(Locale.forLanguageTag("vi-VN"))
    val itemOrder = compareByDescending<ChatListItem> { it.hasMessage }
        .thenByDescending { it.lastMessageAt }
        .thenComparator { first, second ->
            val firstName = first.displayName ?: first.username.orEmpty()
            val secondName = second.displayName ?: second.username.orEmpty()
            vietnameseNames.compare(firstName, secondName)
        }
        .thenBy { it.otherUserId }

    return items.sortedWith(itemOrder)
}

private fun cleanName(value: String?): String? {
    val name = value?.trim()
    if (name.isNullOrEmpty()) {
        return null
    }
    return name
}

fun chatTimestampLabel(
    timestamp: Instant,
    today: LocalDate,
    zone: ZoneId,
    locale: Locale,
    yesterdayLabel: String,
): String {
    val date = timestamp.atZone(zone).toLocalDate()
    if (date == today.minusDays(1)) {
        return yesterdayLabel
    }

    val pattern = when {
        date == today -> "HH:mm"
        date.year == today.year -> "d/M"
        else -> "d/M/yyyy"
    }
    val formatter = DateTimeFormatter.ofPattern(pattern, locale).withZone(zone)
    return formatter.format(timestamp)
}

private val combiningMarks = Regex("\\p{M}+")

private fun normalizedSearch(value: String): String {
    val lowercaseText = value.lowercase(Locale.ROOT)
    val decomposedText = Normalizer.normalize(lowercaseText, Normalizer.Form.NFD)
    val textWithoutAccents = decomposedText.replace(combiningMarks, "")
    return textWithoutAccents.replace('đ', 'd')
}
