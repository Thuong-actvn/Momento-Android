package com.example.momento.feature.friend.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoEmptyState
import com.example.momento.ui.components.MomentoPillButton
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoSearchField
import com.example.momento.ui.components.MomentoSectionHeader
import com.example.momento.ui.components.MomentoTopBar

@Composable
fun FriendScreen(
    state: FriendUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadMore: () -> Unit,
    onSendRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
    onRefreshRequests: () -> Unit = onRefresh,
    onAcceptRequest: (String) -> Unit = {},
    onRejectRequest: (String) -> Unit = {},
    onRemoveFriend: (String) -> Unit = {},
) {
    val searching = state.searchQuery.isNotBlank()
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    var targetRequestId by rememberSaveable { mutableStateOf<String?>(null) }
    var friendToManageId by rememberSaveable { mutableStateOf<String?>(null) }
    var friendToRemoveId by rememberSaveable { mutableStateOf<String?>(null) }
    val friendToManage = state.friends.find { it.id == friendToManageId }
    friendToManage?.let { friend ->
        FriendManagementSheet(
            profile = friend,
            onDismiss = { friendToManageId = null },
            onRemove = {
                friendToManageId = null
                friendToRemoveId = friend.id
            },
        )
    }
    state.friends.find { it.id == friendToRemoveId }?.let { friend ->
        AlertDialog(
            onDismissRequest = { friendToRemoveId = null },
            title = { Text(stringResource(R.string.friend_remove_title)) },
            text = { Text(stringResource(R.string.friend_remove_message, friend.username)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFriend(friend.id)
                    friendToRemoveId = null
                }) { Text(stringResource(R.string.friend_remove_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { friendToRemoveId = null }) {
                    Text(stringResource(R.string.friend_cancel))
                }
            },
        )
    }
    LaunchedEffect(state.searchQuery.trim(), targetRequestId, state.pendingRequests) {
        if (!searching && targetRequestId != null) {
            val index = state.pendingRequests.indexOfFirst { it.requestId == targetRequestId }
            if (index >= 0) {
                val feedbackItems = if (state.pendingError != null) 1 else 0
                listState.scrollToItem(2 + feedbackItems + index)
            }
            targetRequestId = null
        } else if (searching) listState.scrollToItem(0)
    }
    FriendScaffold(
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = if (searching) state.isSearching && state.hasSearched
                else (state.isRefreshing && state.hasLoaded) ||
                    (state.isRefreshingRequests && state.hasLoadedRequests),
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "search_field", contentType = "search") {
                    MomentoSearchField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = stringResource(R.string.friend_search_placeholder),
                        clearContentDescription = stringResource(R.string.friend_search_clear),
                        onSearch = {
                            focusManager.clearFocus()
                            onSearch()
                        },
                    )
                }
                if (searching) searchResultsContent(
                    state, onSearch, onLoadMore, onSendRequest,
                    onManageFriend = { friendToManageId = it },
                ) { requestId ->
                    targetRequestId = requestId
                    focusManager.clearFocus()
                    onSearchQueryChange("")
                }
                else {
                    pendingRequestsContent(state, onRefreshRequests, onAcceptRequest, onRejectRequest)
                    friendListContent(state, onRefresh) { friendId ->
                        friendToManageId = friendId
                    }
                }
            }
        }
    }
}

private fun LazyListScope.pendingRequestsContent(
    state: FriendUiState,
    onRefresh: () -> Unit,
    onAccept: (String) -> Unit,
    onReject: (String) -> Unit,
) {
    item(key = "requests_header", contentType = "header") {
        MomentoSectionHeader(title = stringResource(R.string.friend_request_title),
            count = if (state.hasLoadedRequests) state.pendingRequests.size else null,
            highlighted = true, modifier = Modifier.padding(vertical = 8.dp))
    }
    state.pendingError?.let { error ->
        item(key = "requests_error", contentType = "feedback") {
            MomentoRetryMessage(message = stringResource(error), actionText = stringResource(R.string.friend_retry),
                onRetry = onRefresh, enabled = !state.isRefreshingRequests)
        }
    }
    if (!state.hasLoadedRequests && state.pendingError == null) {
        item(key = "requests_loading", contentType = "feedback") {
            FriendLoading(R.string.friend_pending_loading)
        }
    } else if (state.hasLoadedRequests && state.pendingRequests.isEmpty() && state.pendingError == null) {
        item(key = "requests_empty", contentType = "feedback") {
            Text(text = stringResource(R.string.friend_pending_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp))
        }
    }
    items(state.pendingRequests, key = { "request_${it.requestId}" }, contentType = { "request" }) { request ->
        FriendRequestCard(request = request, onAccept = { onAccept(request.requestId) },
            onReject = { onReject(request.requestId) },
            enabled = request.requester.id !in state.sendingRequestIds,
            isLoading = request.requestId in state.respondingRequestIds,
            error = state.responseErrors[request.requestId]?.let { stringResource(it) })
    }
}

private fun LazyListScope.friendListContent(
    state: FriendUiState,
    onRefresh: () -> Unit,
    onManageFriend: (String) -> Unit,
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
        !state.hasLoaded && state.error == null -> item(key = "loading", contentType = "feedback") {
            FriendLoading()
        }
        state.hasLoaded && state.friends.isEmpty() && state.error == null -> {
            item(key = "empty", contentType = "feedback") { FriendEmptyList() }
        }
        else -> items(state.friends, key = { it.id }, contentType = { "friend" }) { friend ->
            FriendCard(
                profile = friend,
                onManage = { onManageFriend(friend.id) },
                isLoading = friend.id in state.removingFriendIds,
                error = state.removeErrors[friend.id]?.let { stringResource(it) },
            )
        }
    }
}

private fun LazyListScope.searchResultsContent(
    state: FriendUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSendRequest: (String) -> Unit,
    onManageFriend: (String) -> Unit,
    onViewRequest: (String) -> Unit = {},
) {
    item(key = "search_header", contentType = "header") {
        MomentoSectionHeader(
            title = stringResource(R.string.friend_search_results),
            count = if (state.hasSearched) state.searchResults.size else null,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    state.searchError?.let { error ->
        item(key = "search_error", contentType = "feedback") {
            MomentoRetryMessage(
                message = stringResource(error),
                actionText = stringResource(R.string.friend_retry),
                onRetry = onRetry,
                enabled = !state.isSearching,
            )
        }
    }
    when {
        state.isSearching && !state.hasSearched -> {
            item(key = "search_loading", contentType = "feedback") {
                FriendLoading(message = R.string.friend_search_loading)
            }
        }
        state.hasSearched && state.searchResults.isEmpty() && state.searchError == null -> {
            item(key = "search_empty", contentType = "feedback") { FriendEmptySearch() }
        }
        else -> items(state.searchResults, key = { it.id }, contentType = { "search_result" }) { profile ->
            val incoming = state.pendingRequests.find { it.requester.id == profile.id }
            val isFriend = state.friends.any { it.id == profile.id }
            FriendSearchResultCard(
                profile = profile,
                onSendRequest = {
                    if (incoming != null) onViewRequest(incoming.requestId)
                    else onSendRequest(profile.id)
                },
                hasIncomingRequest = incoming != null,
                isLoading = profile.id in state.sendingRequestIds,
                isFriend = isFriend,
                onManage = { onManageFriend(profile.id) },
                isRemoving = profile.id in state.removingFriendIds,
                requestSent = profile.id in state.requestedUserIds,
                error = (if (isFriend) state.removeErrors[profile.id]
                    else state.requestErrors[profile.id])?.let { stringResource(it) },
            )
        }
    }
    if (state.hasMoreSearchResults) {
        item(key = "search_more", contentType = "feedback") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                state.loadMoreError?.let { error ->
                    MomentoRetryMessage(
                        message = stringResource(error),
                        actionText = stringResource(R.string.friend_retry),
                        onRetry = onLoadMore,
                        enabled = !state.isLoadingMoreResults,
                    )
                } ?: MomentoPillButton(
                    text = stringResource(R.string.friend_search_more),
                    onClick = onLoadMore,
                    primary = false,
                    isLoading = state.isLoadingMoreResults,
                )
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
private fun FriendLoading(@StringRes message: Int = R.string.friend_loading) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Text(
            text = stringResource(message),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun FriendEmptySearch() {
    MomentoEmptyState(
        icon = Icons.Outlined.Search,
        title = stringResource(R.string.friend_search_empty_title),
        description = stringResource(R.string.friend_search_empty_description),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 64.dp),
    )
}

@Composable
private fun FriendEmptyList() {
    MomentoEmptyState(
        icon = Icons.Outlined.PeopleOutline,
        title = stringResource(R.string.friend_empty_title),
        description = stringResource(R.string.friend_empty_description),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 64.dp),
    )
}
