package com.example.momento.feature.moment.presentation.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.domain.model.MomentReaction
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoRetryMessage

@Composable
fun JournalReactionsHeader(state: JournalDetailUiState, onLoad: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.journal_reactions_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = onLoad, enabled = !state.isLoadingReactions && !state.isDeleting) {
                Text(stringResource(R.string.journal_view_new_reactions))
            }
        }
        Text(
            text = stringResource(R.string.journal_reactions_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            state.isLoadingReactions -> Text(stringResource(R.string.journal_reactions_loading))
            state.reactionsError != null -> MomentoRetryMessage(
                message = stringResource(state.reactionsError),
                actionText = stringResource(R.string.journal_retry),
                onRetry = onLoad,
                enabled = !state.isDeleting,
            )
            state.showReactions && state.reactions.isEmpty() -> {
                Text(
                    text = stringResource(R.string.journal_reactions_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun JournalReactionRow(reaction: MomentReaction, owner: MomentOwner?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentoAvatar(owner?.avatarUrl, null, Modifier.size(36.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = journalOwnerName(owner),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = journalDetailTime(reaction.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = reaction.emoji, style = MaterialTheme.typography.headlineSmall)
    }
}
