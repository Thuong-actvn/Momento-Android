package com.example.momento.feature.moment.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoRetryMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    modifier: Modifier = Modifier,
    state: JournalUiState = JournalUiState(),
    onFilterSelected: (JournalFilter) -> Unit = {},
    onRetry: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetryFriends: () -> Unit = {},
    onMomentClick: (JournalItem) -> Unit = {},
    onNotificationsClick: () -> Unit = {},
) {
    val sections = journalSections(state.items)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            JournalTopBar(onNotificationsClick = onNotificationsClick)
            JournalFilters(
                friends = state.friends,
                selectedFilter = state.filter,
                onSelect = onFilterSelected,
            )
            if (state.friendsError != null) {
                MomentoRetryMessage(
                    message = stringResource(state.friendsError),
                    actionText = stringResource(R.string.journal_retry),
                    onRetry = onRetryFriends,
                    enabled = !state.isLoadingFriends,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .testTag("journal_friends_error"),
                )
            }
            if (state.error != null && state.items.isNotEmpty()) {
                MomentoRetryMessage(
                    message = stringResource(state.error),
                    actionText = stringResource(R.string.journal_retry),
                    onRetry = onRetry,
                    enabled = !state.isRefreshing,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .testTag("journal_refresh_error"),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = onRefresh,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.items.isNotEmpty()) {
                    key(state.filter) {
                        JournalTimeline(
                            sections = sections,
                            state = state,
                            onRetry = onRetry,
                            onLoadMore = onLoadMore,
                            onMomentClick = onMomentClick,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                JournalStatus(state = state, onRetry = onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalStatus(state: JournalUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.testTag("journal_loading"))
                Text(
                    text = stringResource(R.string.journal_loading),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.error != null -> {
                Text(
                    text = stringResource(state.error),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry, modifier = Modifier.testTag("journal_retry")) {
                    Text(stringResource(R.string.journal_retry))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.journal_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                val message = when (state.filter) {
                    JournalFilter.All -> R.string.journal_empty_message
                    JournalFilter.OnlyMe -> R.string.journal_my_empty_message
                    is JournalFilter.Friend -> R.string.journal_friend_empty_message
                }
                Text(
                    text = stringResource(message),
                    modifier = Modifier.testTag("journal_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
