package com.example.momento.feature.chat.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoEmptyState
import com.example.momento.ui.components.MomentoRetryMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailContent(
    state: ChatDetailUiState,
    onRefresh: () -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val newestMessageId = state.messages.lastOrNull()?.id

    LaunchedEffect(newestMessageId, state.lastSentMessageId) {
        if (newestMessageId != null &&
            (listState.firstVisibleItemIndex == 0 || newestMessageId == state.lastSentMessageId)
        ) {
            listState.scrollToItem(0)
        }
    }

    Column(modifier = modifier) {
        state.historyError?.let { error ->
            MomentoRetryMessage(
                message = stringResource(error),
                actionText = stringResource(R.string.chat_retry),
                onRetry = onRefresh,
                enabled = !state.isLoadingHistory,
                modifier = Modifier.padding(horizontal = 16.dp).testTag("chat_history_error"),
            )
        }

        PullToRefreshBox(
            isRefreshing = state.isLoadingHistory && state.hasLoadedHistory,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.Bottom),
                modifier = Modifier.fillMaxSize().testTag("chat_history"),
            ) {
                items(state.messages.asReversed(), key = { message -> message.id }) { message ->
                    ChatMessageBubble(message = message, isOwnMessage = message.senderId == state.ownUserId)
                }
                if (state.hasMore) {
                    item(key = "older_messages") {
                        ChatOlderMessages(state = state, onLoadOlder = onLoadOlder)
                    }
                }
            }

            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (state.isLoadingHistory && !state.hasLoadedHistory) {
                        CircularProgressIndicator(modifier = Modifier.testTag("chat_history_loading"))
                    } else if (state.hasLoadedHistory) {
                        MomentoEmptyState(
                            icon = Icons.AutoMirrored.Outlined.Chat,
                            title = stringResource(R.string.chat_detail_empty_title),
                            description = stringResource(R.string.chat_detail_empty_description),
                            modifier = Modifier.padding(24.dp).testTag("chat_detail_empty"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatOlderMessages(state: ChatDetailUiState, onLoadOlder: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        val error = state.olderMessagesError
        when {
            state.isLoadingOlder -> CircularProgressIndicator(
                modifier = Modifier.size(24.dp).testTag("chat_older_loading"),
                strokeWidth = 2.dp,
            )
            error != null -> MomentoRetryMessage(
                message = stringResource(error),
                actionText = stringResource(R.string.chat_retry),
                onRetry = onLoadOlder,
                enabled = !state.isLoadingHistory,
                modifier = Modifier.testTag("chat_older_error"),
            )
            else -> TextButton(
                onClick = onLoadOlder,
                enabled = !state.isLoadingHistory,
                modifier = Modifier.testTag("chat_load_older"),
            ) {
                Text(stringResource(R.string.chat_load_older))
            }
        }
    }
}
