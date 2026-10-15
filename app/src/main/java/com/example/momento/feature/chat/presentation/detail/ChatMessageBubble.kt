package com.example.momento.feature.chat.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.chat.domain.model.ChatMessage
import com.example.momento.feature.chat.domain.model.ChatMessageType
import com.example.momento.feature.moment.domain.model.Moment

@Composable
fun ChatMessageBubble(
    message: ChatMessage,
    isOwnMessage: Boolean,
    modifier: Modifier = Modifier,
    moment: Moment? = null,
    isMomentLoading: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().testTag("chat_message_${message.id}"),
        horizontalArrangement = if (isOwnMessage) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (message.type == ChatMessageType.PHOTO_COMMENT) {
                if (moment != null && moment.id == message.referencePhotoId) {
                    ChatMomentPreview(moment = moment)
                } else if (isMomentLoading) {
                    ChatMomentLoading()
                } else {
                    Text(
                        text = stringResource(R.string.chat_moment_unavailable),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Surface(
                modifier = Modifier.testTag("chat_bubble_${message.id}"),
                color = if (isOwnMessage) colors.primary else colors.surfaceContainerLow,
                contentColor = if (isOwnMessage) colors.onPrimary else colors.onSurface,
                shape = RoundedCornerShape(
                    topStart = if (isOwnMessage) 20.dp else 2.dp,
                    topEnd = if (isOwnMessage) 4.dp else 20.dp,
                    bottomEnd = 20.dp,
                    bottomStart = 20.dp,
                ),
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            message.createdAt?.let { timestamp ->
                Text(
                    text = chatMessageTimeLabel(timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
