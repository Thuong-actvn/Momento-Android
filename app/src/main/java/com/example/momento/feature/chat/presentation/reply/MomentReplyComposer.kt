package com.example.momento.feature.chat.presentation.reply

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
import com.example.momento.feature.chat.presentation.detail.ChatInput
import com.example.momento.ui.components.MomentoRetryMessage

@Composable
fun MomentReplyComposer(
    state: MomentReplyUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onCheckDelivery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val placeholder = stringResource(
        if (state.isOwnMoment) R.string.chat_reply_own_moment else R.string.feed_reply_placeholder,
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        state.error?.let { error ->
            if (state.isDeliveryUncertain) {
                MomentoRetryMessage(
                    message = stringResource(error),
                    actionText = stringResource(R.string.chat_reply_check),
                    onRetry = onCheckDelivery,
                    enabled = !state.isCheckingDelivery && !state.isSending,
                    modifier = Modifier.testTag("feed_reply_uncertain"),
                )
            } else {
                Text(
                    text = stringResource(error),
                    color = colors.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("feed_reply_error"),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ChatInput(
                value = state.draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                enabled = state.canReply,
                isError = state.isDraftTooLong,
                placeholder = placeholder,
                modifier = Modifier.weight(1f).testTag("feed_reply"),
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
                modifier = Modifier.size(48.dp).testTag("feed_send_message"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.feed_send_message),
                    modifier = Modifier.size(24.dp).offset(x = 2.dp),
                )
            }
        }
    }
}
