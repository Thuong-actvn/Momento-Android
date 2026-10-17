package com.example.momento.feature.notification.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.notification.domain.model.Notification
import com.example.momento.feature.notification.domain.model.NotificationType
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun NotificationRow(
    notification: Notification,
    now: Instant,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val name = notification.actorDisplayName ?: stringResource(R.string.notification_unknown_actor)
    val description = stringResource(
        when (notification.type) {
            NotificationType.FRIEND_REQUEST -> R.string.notification_friend_request
            NotificationType.FRIEND_ACCEPTED -> R.string.notification_friend_accepted
            NotificationType.UNKNOWN -> R.string.notification_unknown_message
        },
    )
    val icon = when (notification.type) {
        NotificationType.FRIEND_REQUEST -> Icons.Outlined.PersonAdd
        NotificationType.FRIEND_ACCEPTED -> Icons.Outlined.PeopleOutline
        NotificationType.UNKNOWN -> Icons.Outlined.NotificationsNone
    }
    val readStatus = stringResource(
        if (notification.isRead) R.string.notification_read else R.string.notification_unread,
    )
    val text = buildAnnotatedString {
        if (notification.type != NotificationType.UNKNOWN) {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
            append(" ")
        }
        append(description)
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_${notification.id}")
            .semantics(mergeDescendants = true) { stateDescription = readStatus },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surfaceContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (notification.isRead) colors.onSurfaceVariant else colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = notificationTime(notification.createdAt, now),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            if (!notification.isRead) {
                Box(modifier = Modifier.size(8.dp).background(colors.primary, CircleShape))
            }
        }
    }
}

@Composable
private fun notificationTime(createdAt: Instant, now: Instant): String {
    val minutes = Duration.between(createdAt, now).toMinutes()
    val hours = minutes / 60
    return when {
        minutes < 1 -> stringResource(R.string.notification_just_now)
        minutes < 60 -> pluralStringResource(
            R.plurals.notification_minutes_ago,
            minutes.toInt(),
            minutes,
        )
        hours < 24 -> pluralStringResource(
            R.plurals.notification_hours_ago,
            hours.toInt(),
            hours,
        )
        else -> {
            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            createdAt.atZone(ZoneId.systemDefault()).format(formatter)
        }
    }
}
