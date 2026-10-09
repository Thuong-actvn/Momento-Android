package com.example.momento.feature.friend.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoSectionHeader
import com.example.momento.ui.components.MomentoTopBar

@Composable
fun FriendScreen(
    state: FriendUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FriendScaffold(
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing && state.hasLoaded,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "friends_header", contentType = "header") {
                    MomentoSectionHeader(
                        title = stringResource(R.string.friend_list_title),
                        count = if (state.hasLoaded) state.friends.size else null,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                state.error?.let { error ->
                    item(key = "error", contentType = "feedback") {
                        if (!state.hasLoaded) {
                            Text(
                                text = stringResource(R.string.friend_error_title),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(top = 24.dp),
                            )
                        }
                        MomentoRetryMessage(
                            message = stringResource(error),
                            actionText = stringResource(R.string.friend_retry),
                            onRetry = onRefresh,
                            enabled = !state.isRefreshing,
                        )
                    }
                }
                when {
                    !state.hasLoaded && state.error == null -> {
                        item(key = "loading", contentType = "feedback") {
                            FriendLoading()
                        }
                    }
                    state.hasLoaded && state.friends.isEmpty() && state.error == null -> {
                        item(key = "empty", contentType = "feedback") {
                            FriendEmptyList()
                        }
                    }
                    else -> items(state.friends, key = { it.id }, contentType = { "friend" }) { friend ->
                        FriendCard(profile = friend)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FriendScaffold(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            MomentoTopBar(
                title = stringResource(R.string.friend_screen_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.friend_back),
            )
        },
        content = content,
    )
}

@Composable
private fun FriendLoading() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Text(
            text = stringResource(R.string.friend_loading),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun FriendEmptyList() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.PeopleOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.friend_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.friend_empty_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}
