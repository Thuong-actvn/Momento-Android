package com.example.momento.feature.widget.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.widget.domain.model.WidgetFriend
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoCard
import com.example.momento.ui.components.MomentoPillButton
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoSectionHeader
import com.example.momento.ui.components.MomentoTopBar

@Composable
fun WidgetScreen(
    state: WidgetUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onNextPhoto: () -> Unit,
    onSelectFriend: (String?) -> Unit,
    onFriendsClick: () -> Unit,
    onAddWidget: () -> Unit,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MomentoTopBar(
                title = stringResource(R.string.widget_screen_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.widget_back),
                enabled = !state.isSaving,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .selectableGroup(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "preview") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    color = colors.surfaceContainerLow,
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.widget_preview_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                        )
                        WidgetPhotoCard(
                            state = state,
                            onNextPhoto = onNextPhoto,
                        )
                        Button(
                            onClick = onAddWidget,
                            modifier = Modifier.heightIn(min = 40.dp),
                            enabled = !state.isSaving,
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            elevation = null,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.widget_add),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            state.error?.let { error ->
                item(key = "load_error") {
                    MomentoRetryMessage(
                        message = stringResource(error),
                        actionText = stringResource(R.string.widget_retry),
                        onRetry = onRetry,
                        enabled = !state.isLoading,
                    )
                }
            }
            item(key = "source_header") {
                MomentoSectionHeader(
                    title = stringResource(R.string.widget_source_title),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            state.selectionError?.let { error ->
                item(key = "selection_error") {
                    Text(
                        text = stringResource(error),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            item(key = "all_friends") {
                WidgetSourceRow(
                    friend = null,
                    selected = state.selectedFriendId == null,
                    enabled = !state.isSaving && state.snapshot != null,
                    onClick = { onSelectFriend(null) },
                )
            }
            items(state.friends, key = { "friend_${it.id}" }) { friend ->
                WidgetSourceRow(
                    friend = friend,
                    selected = state.selectedFriendId == friend.id,
                    enabled = !state.isSaving,
                    onClick = { onSelectFriend(friend.id) },
                )
            }
            if (state.snapshot != null && state.friends.isEmpty()) {
                item(key = "no_friends") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.widget_no_friends),
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        MomentoPillButton(
                            text = stringResource(R.string.widget_manage_friends),
                            onClick = onFriendsClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetSourceRow(
    friend: WidgetFriend?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val displayName = friend?.displayName
    val title = if (friend == null) {
        stringResource(R.string.widget_all_friends)
    } else if (!displayName.isNullOrBlank()) {
        displayName
    } else {
        friend.username
    }

    MomentoCard(outlined = selected) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (friend == null) {
                Icon(
                    imageVector = Icons.Outlined.PeopleOutline,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                MomentoAvatar(
                    imageUrl = friend.avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (friend != null) {
                    Text(
                        text = stringResource(R.string.widget_username, friend.username),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            RadioButton(
                selected = selected,
                onClick = null,
                enabled = enabled,
            )
        }
    }
}
