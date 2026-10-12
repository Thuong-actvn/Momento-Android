package com.example.momento.feature.moment.presentation.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.momento.feature.moment.presentation.components.MOMENT_IMAGE_ASPECT_RATIO

@Composable
fun FeedPager(
    items: List<FeedItem>,
    initialMomentId: String?,
    actions: FeedActions,
    reactionsEnabled: Boolean = true,
    sentReactions: Map<String, String> = emptyMap(),
) {
    val currentItems by rememberUpdatedState(items)
    val currentActions by rememberUpdatedState(actions)
    // Persist the moment ID in the ViewModel, rather than saving a second page index here.
    val pager = remember {
        val initialPage = items.indexOfFirst { it.moment.id == initialMomentId }.coerceAtLeast(0)
        PagerState(currentPage = initialPage, pageCount = { currentItems.size })
    }
    var reactionMenuId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pager.currentPage) {
        reactionMenuId = null
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage to currentItems.size }.collect { (page, count) ->
            val item = currentItems.getOrNull(page) ?: return@collect
            currentActions.onMomentVisible(item.moment.id)
            if (page >= count - 3) currentActions.onLoadMore()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Fit the shared 4:5 frame into the space left by the fixed controls.
        val cardWidth = minOf(maxWidth - 40.dp, maxHeight * MOMENT_IMAGE_ASPECT_RATIO)
        VerticalPager(
            state = pager,
            key = { currentItems[it].moment.id },
            pageSpacing = 16.dp,
            modifier = Modifier
                .fillMaxSize()
                .testTag("feed_pager"),
        ) { page ->
            val item = currentItems[page]
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MomentCard(
                    item = item,
                    reactionsEnabled = reactionsEnabled,
                    sentEmoji = sentReactions[item.moment.id],
                    showReactions = reactionMenuId == item.moment.id,
                    onShowReactions = { reactionMenuId = item.moment.id },
                    onDismissReactions = { reactionMenuId = null },
                    onReact = { emoji ->
                        reactionMenuId = null
                        actions.onReact(item.moment.id, emoji)
                    },
                    modifier = Modifier.width(cardWidth),
                    compact = cardWidth < 280.dp,
                )
            }
        }
    }
}
