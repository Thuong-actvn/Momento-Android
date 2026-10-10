package com.example.momento.feature.friend.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PauseCircleOutline
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.ui.components.MomentoAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FriendManagementSheet(
    profile: PublicProfile,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                MomentoAvatar(profile.avatarUrl, null, Modifier.size(52.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.friend_username, profile.username),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(colors.surfaceContainer, CircleShape),
                ) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.friend_manage_close))
                }
            }
            HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
            ManagementAction(
                title = stringResource(R.string.friend_manage_pause),
                subtitle = stringResource(R.string.friend_manage_pause_description),
                icon = Icons.Outlined.PauseCircleOutline,
                toggle = true,
            )
            ManagementAction(
                title = stringResource(R.string.friend_manage_priority),
                subtitle = stringResource(R.string.friend_manage_priority_description),
                icon = Icons.Outlined.StarOutline,
            )
            ManagementAction(
                title = stringResource(R.string.friend_manage_memories),
                subtitle = stringResource(R.string.friend_manage_memories_description),
                icon = Icons.Outlined.PhotoLibrary,
            )
            ManagementAction(
                title = stringResource(R.string.friend_manage_remove),
                subtitle = stringResource(R.string.friend_manage_remove_description),
                icon = Icons.Outlined.PersonRemove,
                onClick = onRemove,
            )
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surfaceContainer,
                    contentColor = colors.onSurface,
                ),
            ) {
                Text(stringResource(R.string.friend_manage_close), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun ManagementAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    toggle: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = onClick != null
    val foreground = if (enabled) colors.error else colors.onSurface
    Surface(
        onClick = { onClick?.invoke() },
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) colors.errorContainer.copy(alpha = 0.25f) else colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp).alpha(if (enabled) 1f else 0.5f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    if (enabled) colors.errorContainer else colors.surfaceContainerHigh, CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = foreground) }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = foreground)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (toggle) {
                Switch(checked = false, onCheckedChange = null, enabled = false)
            } else if (!enabled) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}