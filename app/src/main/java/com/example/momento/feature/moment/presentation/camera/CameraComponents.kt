package com.example.momento.feature.moment.presentation.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.momento.R
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoRetryMessage

@Composable
internal fun MomentCaption(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    val focus = LocalFocusManager.current
    val hint = stringResource(R.string.camera_note_hint)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.EditNote, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.limitMomentCaption()) },
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("moment_caption")
                .semantics { contentDescription = hint },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    innerTextField()
                }
            },
        )
        Text(stringResource(R.string.camera_note_count, value.momentCharacterCount()),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun DeferredMomentOptions() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DeferredChip(Icons.Outlined.LocationOn, stringResource(R.string.camera_location))
        DeferredChip(Icons.Outlined.MusicNote, stringResource(R.string.camera_audio))
    }
}

@Composable
private fun DeferredChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(enabled = false, onClick = {}).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, Modifier.size(16.dp), MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        Text(text, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
    }
}

@Composable
internal fun MomentRecipients(
    state: CameraUiState,
    ownAvatarUrl: String?,
    onAudienceSelected: (CameraAudience) -> Unit,
    onFriendToggled: (String) -> Unit,
    onRetry: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.camera_send_to), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val summary = when (state.selection.audience) {
                CameraAudience.ALL_FRIENDS -> stringResource(R.string.camera_audience_all_summary)
                CameraAudience.ONLY_ME -> stringResource(R.string.camera_audience_only_me_summary)
                CameraAudience.SELECTED_FRIENDS -> if (state.selection.isValid)
                    stringResource(R.string.camera_audience_selected, state.selection.friendIds.size)
                else stringResource(R.string.camera_audience_select_hint)
            }
            Text(summary, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("moment_audience_summary"))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "audience:all") {
                RecipientItem(
                    name = stringResource(R.string.camera_audience_all),
                    selected = state.selection.audience == CameraAudience.ALL_FRIENDS,
                    enabled = !state.isBusy, onClick = { onAudienceSelected(CameraAudience.ALL_FRIENDS) },
                    icon = Icons.Outlined.Groups, tag = "recipient_all",
                )
            }
            item(key = "audience:me") {
                RecipientItem(
                    name = stringResource(R.string.camera_audience_only_me),
                    selected = state.selection.audience == CameraAudience.ONLY_ME,
                    enabled = !state.isBusy, onClick = { onAudienceSelected(CameraAudience.ONLY_ME) },
                    icon = if (ownAvatarUrl == null) Icons.Outlined.Lock else null,
                    avatarUrl = ownAvatarUrl, tag = "recipient_me",
                )
            }
            items(state.recipients, key = { "friend:${it.id}" }) { friend ->
                RecipientItem(
                    name = friend.name, avatarUrl = friend.avatarUrl,
                    selected = state.selection.audience == CameraAudience.SELECTED_FRIENDS && friend.id in state.selection.friendIds,
                    enabled = !state.isBusy, onClick = { onFriendToggled(friend.id) },
                    tag = "recipient_${friend.id}",
                )
            }
        }
        when {
            state.isLoadingRecipients -> Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.camera_recipients_loading), style = MaterialTheme.typography.bodySmall)
            }
            state.recipientsError != null -> MomentoRetryMessage(
                message = stringResource(state.recipientsError),
                actionText = stringResource(R.string.camera_retry),
                onRetry = onRetry,
                enabled = !state.isBusy,
            )
            state.hasLoadedRecipients && state.recipients.isEmpty() -> Text(
                stringResource(R.string.camera_recipients_empty), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipientItem(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
    avatarUrl: String? = null,
    icon: ImageVector? = null,
) {
    val description = stringResource(R.string.camera_recipient_description, name)
    Column(
        modifier = Modifier.width(72.dp).clip(RoundedCornerShape(12.dp))
            .selectable(selected, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .testTag(tag).semantics { contentDescription = description }.padding(vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(58.dp)) {
            Box(
                Modifier.size(58.dp).border(if (selected) 2.dp else 1.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape).padding(4.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) Icon(icon, null, Modifier.size(26.dp), MaterialTheme.colorScheme.primary)
                else MomentoAvatar(avatarUrl, null, Modifier.size(50.dp))
            }
            if (selected) Box(
                Modifier.align(Alignment.BottomEnd).size(21.dp).border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                    .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Check, null, Modifier.size(13.dp), MaterialTheme.colorScheme.onPrimary) }
        }
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The action is independently centered; neither side control can shift it. */
@Composable
internal fun MomentActionRow(
    hasDraft: Boolean,
    latestImage: String?,
    isBusy: Boolean,
    actionEnabled: Boolean,
    actionDescription: String,
    onGallery: () -> Unit,
    onAction: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val galleryLabel = stringResource(R.string.camera_open_gallery)
    Box(modifier.fillMaxWidth().heightIn(min = 112.dp).testTag("moment_action_row")) {
        Box(Modifier.align(Alignment.CenterStart).size(52.dp).shadow(2.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(enabled = !isBusy, role = Role.Button, onClick = onGallery)
            .testTag("moment_open_gallery")
            .semantics { contentDescription = galleryLabel }, contentAlignment = Alignment.Center) {
            if (latestImage != null) AsyncImage(latestImage, null, Modifier.size(52.dp), contentScale = ContentScale.Crop)
            else Icon(Icons.Outlined.Image, null, Modifier.size(26.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MomentPrimaryButton(
            hasDraft = hasDraft,
            isBusy = isBusy,
            enabled = actionEnabled,
            description = actionDescription,
            onClick = onAction,
            modifier = Modifier.align(Alignment.Center),
        )
        if (hasDraft) IconButton(onDiscard, enabled = !isBusy,
            modifier = Modifier.align(Alignment.CenterEnd).size(60.dp).testTag("moment_discard")) {
            Icon(Icons.Outlined.Close, stringResource(R.string.camera_discard), Modifier.size(36.dp),
                MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MomentPrimaryButton(
    hasDraft: Boolean,
    isBusy: Boolean,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(88.dp)
            .shadow(12.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .clip(CircleShape).background(MaterialTheme.colorScheme.background)
            .border(if (hasDraft) 4.dp else 3.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = if (hasDraft) 0.25f else 1f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .testTag("moment_primary_action").semantics { contentDescription = description }
            .padding(if (hasDraft) 4.dp else 8.dp)
            .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isBusy -> CircularProgressIndicator(Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            hasDraft -> Icon(Icons.AutoMirrored.Outlined.Send, null, Modifier.size(34.dp), MaterialTheme.colorScheme.onPrimary)
            else -> Icon(painterResource(R.drawable.ic_main_camera), null, Modifier.size(30.dp), MaterialTheme.colorScheme.onPrimary)
        }
    }
}
