package com.example.momento.feature.chat.presentation.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoAvatar
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun ChatListRow(
    item: ChatListItem,
    onClick: () -> Unit,
) {
    val name = item.displayName ?: item.username ?: stringResource(R.string.chat_unknown_user)
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0]
    val yesterday = stringResource(R.string.chat_yesterday)
    val timestamp = if (item.hasMessage && item.lastMessageAt != null) {
        chatTimestampLabel(
            timestamp = item.lastMessageAt,
            today = LocalDate.now(zone),
            zone = zone,
            locale = locale,
            yesterdayLabel = yesterday,
        )
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("chat_peer_${item.otherUserId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentoAvatar(
            imageUrl = item.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (timestamp != null) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.testTag("chat_time_${item.otherUserId}"),
                    )
                }
            }
            Text(
                text = item.lastMessageContent ?: stringResource(R.string.chat_start_conversation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
