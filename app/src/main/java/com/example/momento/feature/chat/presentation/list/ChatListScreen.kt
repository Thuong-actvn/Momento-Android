package com.example.momento.feature.chat.presentation.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoSearchField
import com.example.momento.ui.components.MomentoTopBar

@Composable
fun ChatListScreen(
    state: ChatListUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetryFriends: () -> Unit,
    onRetryConversations: () -> Unit,
    onOpenConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.testTag("chat_list_screen"),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MomentoTopBar(
                title = stringResource(R.string.chat_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.chat_back),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            MomentoSearchField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.chat_search_placeholder),
                clearContentDescription = stringResource(R.string.chat_search_clear),
                onSearch = {
                    focusManager.clearFocus()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .testTag("chat_search"),
            )
            ChatListContent(
                state = state,
                onRefresh = onRefresh,
                onRetryFriends = onRetryFriends,
                onRetryConversations = onRetryConversations,
                onOpenConversation = { userId ->
                    focusManager.clearFocus()
                    onOpenConversation(userId)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}
