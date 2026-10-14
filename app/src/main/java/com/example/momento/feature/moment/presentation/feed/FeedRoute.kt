package com.example.momento.feature.moment.presentation.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R

@Composable
fun FeedRoute(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onMessagesClick: (() -> Unit)? = null,
) {
    // Android Studio previews have no Hilt owner or signed-in session.
    if (LocalInspectionMode.current) {
        FeedScreen(
            modifier = modifier,
            avatarUrl = avatarUrl,
            onAvatarClick = onAvatarClick,
            onMessagesClick = onMessagesClick,
        )
    } else {
        FeedRouteContent(
            viewModel = hiltViewModel(),
            modifier = modifier,
            avatarUrl = avatarUrl,
            onAvatarClick = onAvatarClick,
            onMessagesClick = onMessagesClick,
        )
    }
}

@Composable
fun FeedRouteContent(
    viewModel: FeedViewModel,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onMessagesClick: (() -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message
    val messageText = message?.let { stringResource(it.text) }
    val refreshLabel = stringResource(R.string.feed_reload)

    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            val result = snackbar.showSnackbar(
                message = messageText,
                actionLabel = if (message.canRefresh) refreshLabel else null,
                withDismissAction = message.canRefresh,
                duration = if (message.canRefresh) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.refresh()
            viewModel.clearMessage(message)
        }
    }

    Box(modifier = modifier) {
        FeedScreen(
            modifier = Modifier.fillMaxSize(),
            state = state,
            actions = FeedActions(
                onRetry = viewModel::retryFeed,
                onRetryFriends = viewModel::retryFriends,
                onFilterSelected = viewModel::selectFilter,
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onRetryLoadMore = viewModel::retryLoadMore,
                onMomentVisible = viewModel::showMoment,
                onReact = viewModel::sendReaction,
            ),
            avatarUrl = avatarUrl,
            onAvatarClick = onAvatarClick,
            onMessagesClick = onMessagesClick,
        )
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
