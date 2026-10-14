package com.example.momento.feature.chat.presentation.list

import com.example.momento.R
import com.example.momento.feature.chat.data.remote.ChatError
import com.example.momento.feature.chat.data.remote.ChatException
import com.example.momento.feature.friend.data.remote.FriendError
import com.example.momento.feature.friend.data.remote.FriendException

fun refreshingState(state: ChatListUiState): ChatListUiState {
    return state.copy(
        isRefreshing = true,
        isLoadingFriends = true,
        isLoadingConversations = true,
        friendsError = null,
        conversationsError = null,
    )
}

fun friendsLoadingState(state: ChatListUiState): ChatListUiState {
    return state.copy(
        isLoadingFriends = true,
        friendsError = null,
    )
}

fun friendsLoadedState(
    state: ChatListUiState,
    items: List<ChatListItem>,
): ChatListUiState {
    return state.copy(
        items = items,
        isLoadingFriends = false,
        hasLoadedFriends = true,
        friendsError = null,
        isRefreshing = state.isRefreshing && state.isLoadingConversations,
    )
}

fun friendsErrorState(
    state: ChatListUiState,
    error: Throwable,
): ChatListUiState {
    return state.copy(
        isLoadingFriends = false,
        friendsError = friendErrorMessage(error),
        isRefreshing = state.isRefreshing && state.isLoadingConversations,
    )
}

fun conversationsLoadingState(state: ChatListUiState): ChatListUiState {
    return state.copy(
        isLoadingConversations = true,
        conversationsError = null,
    )
}

fun conversationsLoadedState(state: ChatListUiState): ChatListUiState {
    return state.copy(
        isLoadingConversations = false,
        hasLoadedConversations = true,
        conversationsError = null,
        isRefreshing = state.isRefreshing && state.isLoadingFriends,
    )
}

fun conversationsErrorState(
    state: ChatListUiState,
    error: Throwable,
): ChatListUiState {
    return state.copy(
        isLoadingConversations = false,
        conversationsError = conversationErrorMessage(error),
        isRefreshing = state.isRefreshing && state.isLoadingFriends,
    )
}

private fun friendErrorMessage(error: Throwable): Int {
    val reason = (error as? FriendException)?.reason
    return when (reason) {
        FriendError.UNAUTHENTICATED,
        FriendError.SESSION_CHANGED -> R.string.chat_session_error
        FriendError.NETWORK -> R.string.chat_friends_network_error
        else -> R.string.chat_friends_error
    }
}

private fun conversationErrorMessage(error: Throwable): Int {
    val reason = (error as? ChatException)?.reason
    return when (reason) {
        ChatError.UNAUTHENTICATED,
        ChatError.SESSION_CHANGED -> R.string.chat_session_error
        ChatError.NETWORK -> R.string.chat_conversations_network_error
        ChatError.INVALID_RESPONSE -> R.string.chat_invalid_response
        else -> R.string.chat_conversations_error
    }
}
