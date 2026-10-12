package com.example.momento.feature.moment.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.MomentOwner

@Composable
fun MomentReactionButton(
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    enabled: Boolean = true,
    sentEmoji: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val menuLabel = stringResource(R.string.feed_choose_reaction)
    val description = if (sentEmoji == null) {
        stringResource(R.string.feed_send_heart)
    } else {
        menuLabel
    }
    val sentDescription = sentEmoji?.let { stringResource(R.string.feed_reaction_sent, it) }
    val menuOffset = with(LocalDensity.current) { 56.dp.roundToPx() }

    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerLowest)
                .combinedClickable(
                    enabled = enabled,
                    onClick = {
                        if (sentEmoji == null) {
                            onReact("❤️")
                        }
                    },
                    onLongClick = onExpand,
                    onLongClickLabel = menuLabel,
                    role = Role.Button,
                )
                .semantics {
                    contentDescription = description
                    if (sentDescription != null) stateDescription = sentDescription
                }
                .testTag("feed_react"),
            contentAlignment = Alignment.Center,
        ) {
            if (sentEmoji == null) {
                Icon(
                    imageVector = Icons.Outlined.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(25.dp),
                    tint = colors.primary.copy(alpha = if (enabled) 1f else 0.38f),
                )
            } else {
                Text(
                    text = sentEmoji,
                    fontSize = 24.sp,
                    modifier = Modifier.alpha(if (enabled) 1f else 0.38f),
                )
            }
        }

        if (expanded && enabled) {
            Popup(
                alignment = Alignment.BottomCenter,
                offset = IntOffset(0, -menuOffset),
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                MomentReactionMenu(onReact = onReact)
            }
        }
    }
}

@Composable
private fun MomentReactionMenu(onReact: (String) -> Unit) {
    val emojis = listOf("❤️", "😂", "😍", "😮", "🔥")

    Surface(
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            modifier = Modifier
                .padding(4.dp)
                .testTag("feed_reaction_menu"),
        ) {
            for (emoji in emojis) {
                val description = stringResource(R.string.feed_send_reaction, emoji)

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClick = { onReact(emoji) },
                            role = Role.Button,
                        )
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                    )
                }
            }
        }
    }
}

@Composable
fun DisabledMessageComposer(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.feed_reply_disabled)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainerLowest)
            .heightIn(min = 56.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.feed_reply_placeholder),
            modifier = Modifier
                .weight(1f)
                .semantics {
                    disabled()
                    contentDescription = description
                }
                .testTag("feed_reply"),
            color = colors.onSurface.copy(alpha = 0.38f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("feed_send_message"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Send,
                contentDescription = stringResource(R.string.feed_send_message),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun feedOwnerName(owner: MomentOwner?): String {
    val displayName = owner?.displayName
    if (!displayName.isNullOrBlank()) {
        return displayName
    }

    val username = owner?.username
    if (!username.isNullOrBlank()) {
        return username
    }

    return stringResource(R.string.feed_unknown_owner)
}
