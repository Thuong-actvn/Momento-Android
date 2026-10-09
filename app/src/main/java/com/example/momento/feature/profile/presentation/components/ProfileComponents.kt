package com.example.momento.feature.profile.presentation.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoCard
import com.example.momento.ui.components.MomentoMenuRow

@Composable
fun ProfileIdentityCard(
    user: CurrentUser?,
    onEditProfile: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    MomentoCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MomentoAvatar(
                imageUrl = user?.avatarUrl,
                contentDescription = stringResource(R.string.feed_avatar_description),
                modifier = Modifier
                    .size(96.dp)
                    .border(2.dp, Color(0xFFE4CDC5), CircleShape),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = user?.displayName?.takeIf { it.isNotBlank() }
                    ?: user?.username?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.profile_loading),
                color = colors.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (user != null) {
                Text(
                    text = stringResource(R.string.profile_username, user.username),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(24.dp))
            Surface(
                onClick = onEditProfile,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp),
                shape = RoundedCornerShape(24.dp),
                color = colors.onSurface.copy(alpha = 0.035f),
                contentColor = colors.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.profile_edit),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileConnectionsCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    MomentoCard(modifier = modifier, outlined = true) {
        MomentoMenuRow(
            title = stringResource(R.string.profile_connections),
            subtitle = stringResource(R.string.profile_connections_description),
            badge = stringResource(R.string.profile_new_badge),
            icon = Icons.Outlined.PersonAdd,
            iconColor = colors.primary,
            iconBackground = colors.primary.copy(alpha = 0.1f),
            chevronColor = colors.primary,
        )
    }
}

@Composable
internal fun ProfileSettingsCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    MomentoCard(modifier = modifier) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            MomentoMenuRow(
                title = stringResource(R.string.profile_widgets),
                icon = Icons.Outlined.Widgets,
                iconColor = colors.primary,
            )
            MomentoMenuRow(
                title = stringResource(R.string.profile_notifications),
                icon = Icons.Outlined.NotificationsNone,
                iconColor = colors.secondary,
            )
            MomentoMenuRow(
                title = stringResource(R.string.profile_privacy),
                icon = Icons.Outlined.Lock,
                iconColor = colors.primary,
            )
            MomentoMenuRow(
                title = stringResource(R.string.profile_storage),
                icon = ImageVector.vectorResource(R.drawable.ic_database),
                iconColor = colors.tertiary,
            )
            MomentoMenuRow(
                title = stringResource(R.string.profile_help),
                icon = Icons.AutoMirrored.Outlined.HelpOutline,
                iconColor = colors.onSurfaceVariant,
            )
        }
    }
}
