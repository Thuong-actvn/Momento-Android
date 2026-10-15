package com.example.momento.feature.chat.presentation.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.feature.chat.presentation.list.ChatListViewModel

@Composable
fun ChatDetailRoute(
    otherUserId: String,
    onBack: () -> Unit,
    listViewModel: ChatListViewModel,
    modifier: Modifier = Modifier,
    viewModel: ChatDetailViewModel = hiltViewModel(),
) {
    val listState by listViewModel.state.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val peer = listState.items.find { item -> item.otherUserId == otherUserId }

    ChatDetailScreen(
        peer = peer,
        state = state,
        onBack = onBack,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::send,
        onRefresh = viewModel::refresh,
        onLoadOlder = viewModel::loadOlder,
        modifier = modifier,
    )
}
