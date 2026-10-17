package com.example.momento.feature.notification.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
fun NotificationRoute(
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val resources = LocalResources.current
    val openFriends by rememberUpdatedState(onOpenFriends)

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel, lifecycleOwner, resources) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch {
                viewModel.state.map { it.openFriendsRequestId }
                    .distinctUntilChanged()
                    .filterNotNull()
                    .collect { requestId ->
                        if (viewModel.consumeOpenFriendsRequest(requestId)) {
                            openFriends()
                        }
                    }
            }
            launch {
                viewModel.state.map { it.message }
                    .distinctUntilChanged()
                    .filterNotNull()
                    .collect { message ->
                        snackbar.showSnackbar(resources.getString(message.text))
                        viewModel.clearMessage(message.id)
                    }
            }
        }
    }

    NotificationScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
        onNotificationClick = viewModel::onNotificationClick,
        onMarkAllAsRead = viewModel::markAllAsRead,
        snackbar = snackbar,
        modifier = modifier,
    )
}
