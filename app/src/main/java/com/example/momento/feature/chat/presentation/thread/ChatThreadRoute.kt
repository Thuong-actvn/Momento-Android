package com.example.momento.feature.chat.presentation.thread

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R
import com.example.momento.feature.chat.presentation.list.ChatListItem
import com.example.momento.feature.chat.presentation.list.ChatListViewModel
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoTopBar

@Composable
fun ChatThreadRoute(
    otherUserId: String,
    onBack: () -> Unit,
    listViewModel: ChatListViewModel,
    modifier: Modifier = Modifier,
) {
    val state by listViewModel.state.collectAsStateWithLifecycle()
    val peer = state.items.find { item -> item.otherUserId == otherUserId }

    ChatThreadScreen(
        peer = peer,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun ChatThreadScreen(
    peer: ChatListItem?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = peer?.displayName ?: peer?.username ?: stringResource(R.string.chat_unknown_user)

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.testTag("chat_thread_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MomentoTopBar(
                title = name,
                onBack = onBack,
                backContentDescription = stringResource(R.string.chat_back),
                actions = {
                    MomentoAvatar(
                        imageUrl = peer?.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.chat_thread_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
