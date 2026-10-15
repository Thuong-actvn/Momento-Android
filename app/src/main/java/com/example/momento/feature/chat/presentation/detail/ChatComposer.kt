package com.example.momento.feature.chat.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoRetryMessage

@Composable
fun ChatComposer(
    state: ChatDetailUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.sendError?.let { error ->
            if (state.isDeliveryUncertain) {
                MomentoRetryMessage(
                    message = stringResource(error),
                    actionText = stringResource(R.string.chat_refresh),
                    onRetry = onRefresh,
                    enabled = !state.isLoadingHistory && !state.isLoadingOlder,
                    modifier = Modifier.testTag("chat_send_uncertain"),
                )
            } else {
                Text(
                    text = stringResource(error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("chat_send_error"),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatInput(
                value = state.draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                modifier = Modifier.weight(1f).testTag("chat_draft"),
                enabled = state.ownUserId != null,
                isError = state.isDraftTooLong,
            )
            FilledIconButton(
                onClick = onSend,
                enabled = state.canSend,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = colors.surfaceContainerLow,
                    contentColor = colors.primary,
                    disabledContainerColor = colors.surfaceContainerLow,
                    disabledContentColor = colors.onSurfaceVariant.copy(alpha = 0.38f),
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("chat_send"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    // Cân tâm thị giác của icon Send.
                    modifier = Modifier.size(24.dp).offset(x = 2.dp),
                )
            }
        }
    }
}
