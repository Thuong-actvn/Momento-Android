package com.example.momento.feature.moment.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.AudienceType
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.presentation.components.MomentImageFrame
import com.example.momento.ui.components.MomentoAvatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun JournalDetailScreen(
    state: JournalDetailUiState,
    friends: List<MomentOwner>,
    onClose: () -> Unit,
    onLoadReactions: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onDelete: () -> Unit,
    backContentDescription: String = stringResource(R.string.journal_close_detail),
) {
    val item = state.item ?: return
    val ownerName = if (item.isOwn) {
        stringResource(R.string.journal_you)
    } else {
        journalOwnerName(item.owner)
    }
    val view = LocalView.current
    val lightBackground = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = lightBackground
        controller.isAppearanceLightNavigationBars = lightBackground
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("journal_detail")) {
            JournalDetailTopBar(
                isOwn = item.isOwn,
                enabled = !state.isDeleting,
                onClose = onClose,
                onDelete = onRequestDelete,
                backContentDescription = backContentDescription,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    MomentImageFrame(modifier = Modifier.fillMaxWidth()) {
                        JournalPhoto(
                            imageUrl = item.moment.imageUrl,
                            contentDescription = stringResource(R.string.journal_image_description, ownerName),
                        )
                    }
                }
                item { JournalDetailInfo(item = item, ownerName = ownerName) }
                if (item.isOwn) {
                    item {
                        JournalReactionsHeader(state = state, onLoad = onLoadReactions)
                    }
                    items(items = state.reactions, key = { it.id }) { reaction ->
                        JournalReactionRow(
                            reaction = reaction,
                            owner = friends.firstOrNull { it.id == reaction.userId },
                        )
                    }
                }
            }
        }
    }
    if (state.showDeleteConfirmation) {
        JournalDeleteConfirmation(state = state, onDismiss = onDismissDelete, onDelete = onDelete)
    }
}

@Composable
private fun JournalDetailTopBar(
    isOwn: Boolean,
    enabled: Boolean,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    backContentDescription: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose, enabled = enabled) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, backContentDescription)
        }
        Text(
            text = stringResource(R.string.journal_detail_title),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        if (isOwn) {
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.journal_delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun JournalDetailInfo(item: JournalItem, ownerName: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MomentoAvatar(item.owner?.avatarUrl, null, Modifier.size(40.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ownerName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = journalDetailTime(item.moment.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        val caption = item.moment.caption
        if (!caption.isNullOrBlank()) {
            Text(text = caption, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = stringResource(
                when (item.moment.audienceType) {
                    AudienceType.ALL_FRIENDS -> R.string.journal_audience_all
                    AudienceType.CUSTOM -> R.string.journal_audience_custom
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JournalDeleteConfirmation(
    state: JournalDetailUiState,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isDeleting) onDismiss() },
        title = { Text(stringResource(R.string.journal_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.journal_delete_confirmation))
                state.deleteError?.let { error ->
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete, enabled = !state.isDeleting) {
                Text(
                    text = stringResource(
                        if (state.isDeleting) R.string.journal_deleting else R.string.journal_delete,
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isDeleting) {
                Text(stringResource(R.string.journal_cancel))
            }
        },
    )
}

@Composable
fun journalDetailTime(createdAt: Instant?): String {
    if (createdAt == null) return stringResource(R.string.journal_unknown_date)
    val locale = LocalConfiguration.current.locales[0]
    return DateTimeFormatter.ofPattern("HH:mm · d/M/yyyy", locale)
        .withZone(ZoneId.systemDefault())
        .format(createdAt)
}
