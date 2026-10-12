package com.example.momento.feature.moment.presentation.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.presentation.components.MOMENT_IMAGE_ASPECT_RATIO
import com.example.momento.feature.moment.presentation.components.MomentImage
import com.example.momento.ui.components.MomentoAvatar
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun JournalCard(
    item: JournalItem,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    val ownerName = if (item.isOwn) {
        stringResource(R.string.journal_you)
    } else {
        journalOwnerName(item.owner)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(MOMENT_IMAGE_ASPECT_RATIO)
            .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f))
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .testTag("journal_card_${item.moment.id}"),
    ) {
        JournalPhoto(
            imageUrl = item.moment.imageUrl,
            contentDescription = stringResource(R.string.journal_image_description, ownerName),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                ),
        )

        if (item.isOwn) {
            Text(
                text = stringResource(R.string.journal_own_badge),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        JournalCardFooter(
            item = item,
            ownerName = ownerName,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

private enum class JournalPhotoStatus {
    LOADING,
    SUCCESS,
    ERROR,
}

@Composable
private fun JournalPhoto(imageUrl: String, contentDescription: String) {
    var retryCount by remember(imageUrl) { mutableIntStateOf(0) }
    var status by remember(imageUrl, retryCount) {
        mutableStateOf(if (imageUrl.isBlank()) JournalPhotoStatus.ERROR else JournalPhotoStatus.LOADING)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (imageUrl.isNotBlank()) {
            key(imageUrl, retryCount) {
                MomentImage(
                    image = imageUrl,
                    contentDescription = contentDescription,
                    onSuccess = { status = JournalPhotoStatus.SUCCESS },
                    onError = { status = JournalPhotoStatus.ERROR },
                )
            }
        }

        when (status) {
            JournalPhotoStatus.LOADING -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                    strokeWidth = 2.dp,
                )
            }
            JournalPhotoStatus.ERROR -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                        .testTag("journal_image_error"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.journal_image_error),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = { retryCount++ }) {
                        Text(
                            text = stringResource(R.string.journal_retry),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            JournalPhotoStatus.SUCCESS -> Unit
        }
    }
}

@Composable
private fun JournalCardFooter(
    item: JournalItem,
    ownerName: String,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val time = item.moment.createdAt?.let { createdAt ->
        DateTimeFormatter.ofPattern("HH:mm", locale)
            .withZone(ZoneId.systemDefault())
            .format(createdAt)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MomentoAvatar(
                imageUrl = item.owner?.avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = ownerName,
                modifier = Modifier
                    .weight(1f)
                    .testTag("journal_owner_name"),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (time != null) {
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}
