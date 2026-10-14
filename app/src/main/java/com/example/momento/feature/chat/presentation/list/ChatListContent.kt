package com.example.momento.feature.chat.presentation.list

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoRetryMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListContent(
    state: ChatListUiState,
    onRefresh: () -> Unit,
    onRetryFriends: () -> Unit,
    onRetryConversations: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var previousQuery by rememberSaveable { mutableStateOf(state.query) }

    LaunchedEffect(state.query) {
        if (previousQuery != state.query) {
            listState.scrollToItem(0)
        }
        previousQuery = state.query
    }

    val visibleItems = state.visibleItems
    val hasItems = state.items.isNotEmpty()
    val hasVisibleItems = visibleItems.isNotEmpty()
    val hasCompleteList = state.hasLoadedFriends && state.hasLoadedConversations &&
        state.friendsError == null && state.conversationsError == null

    val showInitialLoading = state.isLoading && !hasItems && !state.isRefreshing
    val canShowEmptyState = hasCompleteList && (!state.isLoading || state.isRefreshing)
    val showSearchEmpty = state.query.isNotBlank() && !hasVisibleItems &&
        (hasItems || canShowEmptyState)
    val showEmptyList = !hasVisibleItems && canShowEmptyState

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("chat_list"),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            val friendsError = state.friendsError
            if (friendsError != null) {
                item(key = "friends_error", contentType = "feedback") {
                    MomentoRetryMessage(
                        message = stringResource(friendsError),
                        actionText = stringResource(R.string.chat_retry),
                        onRetry = onRetryFriends,
                        enabled = !state.isLoadingFriends,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .testTag("chat_friends_error"),
                    )
                }
            }

            val conversationsError = state.conversationsError
            if (conversationsError != null) {
                item(key = "conversations_error", contentType = "feedback") {
                    MomentoRetryMessage(
                        message = stringResource(conversationsError),
                        actionText = stringResource(R.string.chat_retry),
                        onRetry = onRetryConversations,
                        enabled = !state.isLoadingConversations,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .testTag("chat_conversations_error"),
                    )
                }
            }

            items(
                items = visibleItems,
                key = { item -> "peer_${item.otherUserId}" },
                contentType = { "conversation" },
            ) { item ->
                ChatListRow(
                    item = item,
                    onClick = {
                        onOpenConversation(item.otherUserId)
                    },
                )
            }

            when {
                showInitialLoading -> {
                    item(key = "loading", contentType = "feedback") {
                        ChatListLoading()
                    }
                }
                showSearchEmpty -> {
                    item(key = "search_empty", contentType = "feedback") {
                        ChatListEmpty(searching = true)
                    }
                }
                showEmptyList -> {
                    item(key = "empty", contentType = "feedback") {
                        ChatListEmpty(searching = false)
                    }
                }
            }
        }
    }
}
