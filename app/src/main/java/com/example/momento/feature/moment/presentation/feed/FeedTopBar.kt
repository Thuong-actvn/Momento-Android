package com.example.momento.feature.moment.presentation.feed

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoAvatar

@Composable
fun FeedTopBar(
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_app),
            contentDescription = null,
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = {}, enabled = false, modifier = Modifier.size(48.dp).testTag("feed_messages")) {
            Icon(
                painter = painterResource(R.drawable.ic_feed_message),
                contentDescription = stringResource(R.string.feed_messages),
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(4.dp))

        if (onAvatarClick == null) {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                MomentoAvatar(
                    imageUrl = avatarUrl,
                    contentDescription = stringResource(R.string.feed_avatar_description),
                )
            }
        } else {
            val openProfileLabel = stringResource(R.string.feed_open_profile)
            IconButton(
                onClick = onAvatarClick,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = openProfileLabel
                },
            ) {
                MomentoAvatar(imageUrl = avatarUrl, contentDescription = null)
            }
        }
    }
}
