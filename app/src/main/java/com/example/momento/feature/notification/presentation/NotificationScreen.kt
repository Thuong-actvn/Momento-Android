package com.example.momento.feature.notification.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoEmptyState
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoTopBar
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    state: NotificationUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetryLoadMore: () -> Unit = {},
    onNotificationClick: (String) -> Unit = {},
    onMarkAllAsRead: () -> Unit = {},
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    now: Instant = Instant.now(),
) {
    val listState = rememberLazyListState()

    Scaffold(
        modifier = modifier.testTag("notification_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MomentoTopBar(
                title = stringResource(R.string.notification_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.notification_back),
                actions = {
                    TextButton(
                        onClick = onMarkAllAsRead,
                        enabled = state.canMarkAllAsRead,
                        modifier = Modifier.testTag("notification_read_all"),
                    ) {
                        Text(stringResource(R.string.notification_read_all))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("notification_list"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.items.isEmpty()) {
                    item(key = "status") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            NotificationStatus(state, onRetry)
                        }
                    }
                } else {
                    if (state.error != null) {
                        item(key = "refresh_error") {
                            MomentoRetryMessage(
                                message = stringResource(state.error),
                                actionText = stringResource(R.string.notification_retry),
                                onRetry = onRetry,
                                enabled = !state.isRefreshing,
                                modifier = Modifier.testTag("notification_refresh_error"),
                            )
                        }
                    }

                    items(state.items, key = { it.id }) { notification ->
                        NotificationRow(
                            notification = notification,
                            now = now,
                            onClick = { onNotificationClick(notification.id) },
                        )
                    }

                    if (state.hasMore && !state.isRefreshing && state.error == null && !state.isMarkingAllAsRead) {
                        item(key = "pagination") {
                            if (state.loadMoreError != null) {
                                MomentoRetryMessage(
                                    message = stringResource(state.loadMoreError),
                                    actionText = stringResource(R.string.notification_retry),
                                    onRetry = onRetryLoadMore,
                                    modifier = Modifier.testTag("notification_load_more_error"),
                                )
                            } else {
                                LaunchedEffect(state.items.last().id) {
                                    onLoadMore()
                                }
                                NotificationLoading(
                                    text = stringResource(R.string.notification_loading_more),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                        .testTag("notification_loading_more"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationStatus(state: NotificationUiState, onRetry: () -> Unit) {
    when {
        state.isLoading -> NotificationLoading(
            text = stringResource(R.string.notification_loading),
            modifier = Modifier.testTag("notification_loading"),
        )
        state.error != null -> MomentoRetryMessage(
            message = stringResource(state.error),
            actionText = stringResource(R.string.notification_retry),
            onRetry = onRetry,
            modifier = Modifier.testTag("notification_error"),
        )
        else -> MomentoEmptyState(
            icon = Icons.Outlined.NotificationsNone,
            title = stringResource(R.string.notification_empty_title),
            description = stringResource(R.string.notification_empty_message),
            modifier = Modifier.testTag("notification_empty"),
        )
    }
}

@Composable
private fun NotificationLoading(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
