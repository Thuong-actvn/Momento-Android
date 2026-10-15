package com.example.momento.feature.chat.presentation.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.presentation.components.MomentImageFrame
import com.example.momento.feature.moment.presentation.feed.FeedItem
import com.example.momento.feature.moment.presentation.feed.MomentCard

@Composable
fun ChatMomentPreview(
    moment: Moment,
    modifier: Modifier = Modifier,
) {
    MomentCard(
        item = FeedItem(moment = moment),
        compact = true,
        reactionsVisible = false,
        showOwner = false,
        modifier = modifier
            .width(220.dp)
            .testTag("chat_moment_${moment.id}"),
    )
}

@Composable
fun ChatMomentLoading(modifier: Modifier = Modifier) {
    MomentImageFrame(modifier = modifier.width(220.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}
