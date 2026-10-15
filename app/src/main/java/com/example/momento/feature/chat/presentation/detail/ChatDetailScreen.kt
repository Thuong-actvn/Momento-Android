package com.example.momento.feature.chat.presentation.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.chat.presentation.list.ChatListItem

@Composable
fun ChatDetailScreen(
    peer: ChatListItem?,
    state: ChatDetailUiState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    onLoadOlder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = peer?.displayName?.takeIf { it.isNotBlank() }
        ?: peer?.username?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.chat_unknown_user)

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier
            .testTag("chat_detail_screen")
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatDetailTopBar(
                displayName = name,
                avatarUrl = peer?.avatarUrl,
                onBack = onBack,
            )
        },
        bottomBar = {
            ChatComposer(
                state = state,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onRefresh = onRefresh,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        },
    ) { innerPadding ->
        ChatDetailContent(
            state = state,
            onRefresh = onRefresh,
            onLoadOlder = onLoadOlder,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        )
    }
}
