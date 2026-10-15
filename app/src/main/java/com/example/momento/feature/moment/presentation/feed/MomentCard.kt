package com.example.momento.feature.moment.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.AudienceType
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.presentation.components.MomentImage
import com.example.momento.feature.moment.presentation.components.MomentImageFrame
import com.example.momento.ui.components.MomentoAvatar
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MomentCard(
    item: FeedItem,
    modifier: Modifier = Modifier,
    showReactions: Boolean = false,
    onShowReactions: () -> Unit = {},
    onDismissReactions: () -> Unit = {},
    onReact: (String) -> Unit = {},
    compact: Boolean = false,
    reactionsEnabled: Boolean = true,
    sentEmoji: String? = null,
    reactionsVisible: Boolean = true,
    showOwner: Boolean = true,
) {
    val moment = item.moment
    val ownerName = feedOwnerName(item.owner)

    MomentImageFrame(
        modifier = modifier.testTag("feed_image_frame_${moment.id}"),
    ) {
        FeedPhoto(
            momentId = moment.id,
            imageUrl = moment.imageUrl,
            contentDescription = if (showOwner) {
                stringResource(R.string.feed_image_description, ownerName)
            } else {
                stringResource(R.string.feed_image_only_description)
            },
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.42f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                ),
        )

        MomentCardHeader(
            owner = item.owner,
            createdAt = moment.createdAt,
            compact = compact,
            showOwner = showOwner,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        MomentCardFooter(
            item = item,
            compact = compact,
            showReactions = showReactions,
            onShowReactions = onShowReactions,
            onDismissReactions = onDismissReactions,
            onReact = onReact,
            reactionsEnabled = reactionsEnabled,
            sentEmoji = sentEmoji,
            reactionsVisible = reactionsVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private enum class FeedPhotoStatus {
    LOADING,
    SUCCESS,
    ERROR,
}

@Composable
private fun FeedPhoto(
    momentId: String,
    imageUrl: String,
    contentDescription: String,
) {
    var retryCount by remember(imageUrl) { mutableIntStateOf(0) }
    var status by remember(imageUrl, retryCount) { mutableStateOf(FeedPhotoStatus.LOADING) }

    Box(modifier = Modifier.fillMaxSize()) {
        // A new key recreates the image request when the user retries.
        key(imageUrl, retryCount) {
            MomentImage(
                image = imageUrl,
                contentDescription = contentDescription,
                onSuccess = { status = FeedPhotoStatus.SUCCESS },
                onError = { status = FeedPhotoStatus.ERROR },
            )
        }

        when (status) {
            FeedPhotoStatus.LOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                )
            }
            FeedPhotoStatus.ERROR -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.feed_image_error),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { retryCount++ },
                        modifier = Modifier.testTag("feed_image_retry_$momentId"),
                    ) {
                        Text(text = stringResource(R.string.feed_retry))
                    }
                }
            }
            FeedPhotoStatus.SUCCESS -> Unit
        }
    }
}

@Composable
private fun MomentCardHeader(
    owner: MomentOwner?,
    createdAt: Instant?,
    compact: Boolean,
    showOwner: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (showOwner) {
                MomentOwnerBadge(
                    owner = owner,
                    createdAt = createdAt,
                    compact = compact,
                )
            } else {
                val time = feedTimeLabel(createdAt)
                if (time != null) {
                    MomentTime(
                        time = time,
                        modifier = Modifier
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.background)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.background)
                .semantics { disabled() }
                .testTag("feed_audio"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.GraphicEq,
                contentDescription = stringResource(R.string.feed_audio_unavailable),
                modifier = Modifier.size(20.dp),
                tint = colors.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun MomentOwnerBadge(
    owner: MomentOwner?,
    createdAt: Instant?,
    compact: Boolean,
) {
    val ownerName = feedOwnerName(owner)
    val time = feedTimeLabel(createdAt)

    Row(
        modifier = Modifier
            .testTag("feed_owner_badge")
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp),
    ) {
        MomentoAvatar(
            imageUrl = owner?.avatarUrl,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 24.dp else 30.dp),
        )
        if (compact) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = ownerName,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("feed_owner_name"),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (time != null) {
                    MomentTime(time = time)
                }
            }
        } else {
            Text(
                text = ownerName,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .widthIn(max = 120.dp)
                    .testTag("feed_owner_name"),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (time != null) {
                MomentTime(time = "· $time")
            }
        }
    }
}

@Composable
private fun MomentTime(time: String, modifier: Modifier = Modifier) {
    Text(
        text = time,
        modifier = modifier.testTag("feed_moment_time"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MomentCardFooter(
    item: FeedItem,
    compact: Boolean,
    showReactions: Boolean,
    onShowReactions: () -> Unit,
    onDismissReactions: () -> Unit,
    onReact: (String) -> Unit,
    reactionsEnabled: Boolean,
    sentEmoji: String?,
    reactionsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(if (compact) 12.dp else 20.dp)
            .testTag("feed_caption_row"),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val caption = item.moment.caption
            if (!caption.isNullOrBlank()) {
                Text(
                    text = caption,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MomentDetails(
                locationName = item.locationName,
                audienceType = item.moment.audienceType,
                compact = compact,
            )
        }
        if (reactionsVisible) {
            MomentReactionButton(
                enabled = reactionsEnabled,
                sentEmoji = sentEmoji,
                expanded = showReactions,
                onExpand = onShowReactions,
                onDismiss = onDismissReactions,
                onReact = onReact,
            )
        }
    }
}

@Composable
private fun MomentDetails(
    locationName: String?,
    audienceType: AudienceType,
    compact: Boolean,
) {
    val detailsColor = Color(0xFFFFDAD2).copy(alpha = 0.9f)
    val audienceLabel = stringResource(
        if (audienceType == AudienceType.ALL_FRIENDS) {
            R.string.feed_audience_all
        } else {
            R.string.feed_audience_custom
        },
    )
    val hasLocation = !locationName.isNullOrBlank()
    val details = if (hasLocation) "$locationName · $audienceLabel" else audienceLabel

    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (hasLocation) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = stringResource(R.string.feed_location),
                modifier = Modifier
                    .size(14.dp)
                    .testTag("feed_location"),
                tint = detailsColor,
            )
        }
        Text(
            text = details,
            modifier = Modifier.weight(1f),
            color = detailsColor,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun feedTimeLabel(createdAt: Instant?): String? {
    if (createdAt == null) {
        return null
    }

    val minutes = Duration.between(createdAt, Instant.now())
        .toMinutes()
        .coerceAtLeast(0)

    return when {
        minutes < 1 -> stringResource(R.string.feed_time_now)
        minutes < 60 -> stringResource(R.string.feed_time_minutes, minutes)
        minutes < 24 * 60 -> stringResource(R.string.feed_time_hours, minutes / 60)
        minutes < 7 * 24 * 60 -> stringResource(R.string.feed_time_days, minutes / (24 * 60))
        else -> DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault())
            .format(createdAt)
    }
}
