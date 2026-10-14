package com.example.momento.feature.chat.presentation.list

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChatListRoute(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)

    ChatListScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onRefresh = viewModel::refresh,
        onRetryFriends = viewModel::retryFriends,
        onRetryConversations = viewModel::retryConversations,
        onOpenConversation = onOpenConversation,
        modifier = modifier,
    )
}
