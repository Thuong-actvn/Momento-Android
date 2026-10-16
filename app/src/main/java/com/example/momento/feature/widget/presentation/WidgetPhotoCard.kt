package com.example.momento.feature.widget.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.example.momento.R
import com.example.momento.ui.components.MomentoAvatar

@Composable
fun WidgetPhotoCard(
    state: WidgetUiState,
    onRefresh: () -> Unit,
    onNextPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val photo = state.photo
    val sender = state.sender
    val displayName = sender?.displayName
    val senderName = if (!displayName.isNullOrBlank()) {
        displayName
    } else {
        sender?.username.orEmpty()
    }
    val headerContentColor = if (photo != null) Color.White else colors.primary
    val overlayTextStyle = MaterialTheme.typography.labelLarge.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

    Box(
        modifier = modifier
            .size(232.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colors.surfaceContainer),
    ) {
        if (photo != null) {
            SubcomposeAsyncImage(
                model = photo.imageUrl,
                contentDescription = stringResource(R.string.widget_photo_description, senderName),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.widget_photo_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                },
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.4f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.8f),
                        ),
                    ),
            )
        } else {
            val emptyTitle = if (state.isRefreshing && state.snapshot == null) {
                R.string.widget_loading
            } else {
                R.string.widget_photo_empty
            }
            val emptyDescription = when {
                state.hasUnavailableFriend -> R.string.widget_friend_unavailable
                state.selectedFriendId != null -> R.string.widget_single_empty_description
                else -> R.string.widget_photo_empty_description
            }

            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Photo,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(36.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(emptyTitle),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(emptyDescription),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                modifier = Modifier.weight(1f),
                style = overlayTextStyle,
                color = headerContentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = onRefresh,
                // Align the visible icon with the label's glyphs rather than its line box.
                modifier = Modifier.offset(y = 1.dp),
                enabled = !state.isRefreshing,
            ) {
                if (state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = headerContentColor,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = stringResource(R.string.widget_refresh),
                        modifier = Modifier.size(24.dp),
                        tint = headerContentColor,
                    )
                }
            }
        }

        if (photo != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MomentoAvatar(
                        imageUrl = sender?.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = senderName,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        style = overlayTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.selectedFriendId == null) {
                        val canGoToNextPhoto = state.photos.size > 1
                        val nextIconColor = if (canGoToNextPhoto) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.4f)
                        }

                        IconButton(
                            onClick = onNextPhoto,
                            enabled = canGoToNextPhoto,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = stringResource(R.string.widget_next),
                                modifier = Modifier.size(24.dp),
                                tint = nextIconColor,
                            )
                        }
                    }
                }
                val caption = photo.caption
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
