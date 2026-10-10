package com.example.momento.feature.friend.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.domain.model.FriendRequest
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoCard
import com.example.momento.ui.components.MomentoPillButton

@Composable
internal fun FriendCard(
    profile: PublicProfile,
    modifier: Modifier = Modifier,
    onManage: (() -> Unit)? = null,
) {
    MomentoCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FriendIdentity(
                profile = profile,
                subtitle = stringResource(R.string.friend_connected),
                connected = true,
                modifier = Modifier.weight(1f),
            )
            onManage?.let {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.friend_options, profile.username),
                    )
                }
            }
        }
    }
}

@Composable
internal fun FriendRequestCard(
    request: FriendRequest,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    error: String? = null,
) {
    MomentoCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FriendIdentity(
                profile = request.requester,
                subtitle = stringResource(R.string.friend_request_description),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MomentoPillButton(
                    text = stringResource(R.string.friend_request_reject),
                    onClick = onReject,
                    primary = false,
                    enabled = enabled && !isLoading,
                )
                MomentoPillButton(
                    text = stringResource(R.string.friend_request_accept),
                    onClick = onAccept,
                    leadingIcon = Icons.Outlined.Check,
                    enabled = enabled,
                    isLoading = isLoading,
                )
            }
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
    }
}

@Composable
internal fun FriendSearchResultCard(
    profile: PublicProfile,
    onSendRequest: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    isFriend: Boolean = false,
    requestSent: Boolean = false,
    hasIncomingRequest: Boolean = false,
    error: String? = null,
 ) {
    MomentoCard(modifier = modifier, shape = MaterialTheme.shapes.extraLarge) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (isFriend) {
                FriendIdentity(
                    profile = profile,
                    subtitle = stringResource(R.string.friend_connected),
                    connected = true,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FriendIdentity(profile = profile, modifier = Modifier.weight(1f))
                    MomentoPillButton(
                        text = stringResource(
                            if (hasIncomingRequest) R.string.friend_view_request
                            else if (requestSent) R.string.friend_request_sent
                            else R.string.friend_request_send,
                        ),
                        onClick = onSendRequest,
                        primary = !requestSent && !hasIncomingRequest,
                        enabled = enabled && (hasIncomingRequest || !requestSent),
                        isLoading = isLoading,
                    )
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

@Composable
private fun FriendIdentity(
    profile: PublicProfile,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    connected: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MomentoAvatar(
            imageUrl = profile.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.friend_username, profile.username),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitle?.let {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (connected) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.secondary,
                        )
                    }
                    Text(
                        text = it,
                        color = if (connected) colors.secondary else colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
