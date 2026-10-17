package com.example.momento.app.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun MainRoute(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onAvatarClick: (() -> Unit)? = null,
    onMessagesClick: (() -> Unit)? = null,
    onOpenConversation: ((String) -> Unit)? = null,
    onNotificationsClick: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    MainScreen(
        onExit = onExit,
        modifier = modifier,
        avatarUrl = currentUser?.avatarUrl,
        onAvatarClick = onAvatarClick,
        onMessagesClick = onMessagesClick,
        onOpenConversation = onOpenConversation,
        onNotificationsClick = onNotificationsClick,
    )
}
