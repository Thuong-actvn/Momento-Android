package com.example.momento.feature.friend.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.feature.friend.domain.model.FriendRequestAction

@Composable
fun FriendRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Also handles the first visit; the ViewModel ignores overlapping refreshes.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshFriends()
    }
    BackHandler(onBack = onBack)

    FriendScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onSearch = viewModel::retrySearch,
        onLoadMore = viewModel::loadMoreSearchResults,
        onSendRequest = viewModel::sendRequest,
        onRefreshRequests = viewModel::refreshRequests,
        onAcceptRequest = { viewModel.respondRequest(it, FriendRequestAction.ACCEPT) },
        onRejectRequest = { viewModel.respondRequest(it, FriendRequestAction.REJECT) },
        modifier = modifier,
    )
}
