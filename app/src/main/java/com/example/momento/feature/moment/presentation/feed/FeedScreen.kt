package com.example.momento.feature.moment.presentation.feed

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.AudienceType
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.presentation.components.MOMENT_IMAGE_ASPECT_RATIO
import com.example.momento.ui.theme.MomentoTheme
import java.time.Instant

data class FeedItem(
    val moment: Moment,
    val owner: MomentOwner? = null,
    // UI data only; map this once the backend defines the location metadata contract.
    val locationName: String? = null,
)

sealed interface FeedFilter {
    data object All : FeedFilter
    data object OnlyMe : FeedFilter
    data class Friend(val id: String) : FeedFilter
}

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    val friends: List<MomentOwner> = emptyList(),
    val filter: FeedFilter = FeedFilter.All,
    val isLoading: Boolean = false,
    @param:StringRes val error: Int? = null,
)

data class FeedActions(
    val onRetry: () -> Unit = {},
    val onFilterSelected: (FeedFilter) -> Unit = {},
    val onReact: (momentId: String, emoji: String) -> Unit = { _, _ -> },
)

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    state: FeedUiState = FeedUiState(),
    actions: FeedActions = FeedActions(),
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            FeedTopBar(avatarUrl = avatarUrl, onAvatarClick = onAvatarClick)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.items.isNotEmpty()) {
                    // Changing the filter starts at the first photo; ordinary recomposition keeps the page.
                    key(state.filter) { FeedPager(state.items, actions.onReact) }
                } else {
                    FeedStatus(state, actions.onRetry)
                }
            }
            FeedFriendFilter(
                friends = state.friends,
                selectedFilter = state.filter,
                onSelect = actions.onFilterSelected,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            DisabledMessageComposer(Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FeedPager(items: List<FeedItem>, onReact: (String, String) -> Unit) {
    val pager = rememberPagerState(pageCount = { items.size })
    var reactionMenuId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pager.currentPage) { reactionMenuId = null }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Fit a 4:5 card into the space left by the fixed header, filter and reply row.
        val cardWidth = minOf(maxWidth - 40.dp, maxHeight * MOMENT_IMAGE_ASPECT_RATIO)
        VerticalPager(
            state = pager,
            key = { items[it].moment.id },
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize().testTag("feed_pager"),
        ) { page ->
            val item = items[page]
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MomentCard(
                    item = item,
                    showReactions = reactionMenuId == item.moment.id,
                    onShowReactions = { reactionMenuId = item.moment.id },
                    onDismissReactions = { reactionMenuId = null },
                    onReact = { emoji ->
                        reactionMenuId = null
                        onReact(item.moment.id, emoji)
                    },
                    modifier = Modifier.width(cardWidth),
                    compact = cardWidth < 280.dp,
                )
            }
        }
    }
}

@Composable
private fun FeedStatus(state: FeedUiState, onRetry: () -> Unit) {
    Column(
        Modifier.padding(24.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(Modifier.testTag("feed_loading"))
                Text(stringResource(R.string.feed_loading), Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium)
            }
            state.error != null -> {
                Text(stringResource(state.error), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("feed_retry")) {
                    Text(stringResource(R.string.feed_retry))
                }
            }
            else -> {
                Text(stringResource(R.string.feed_empty_title), style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center)
                val message = when (state.filter) {
                    FeedFilter.All -> R.string.feed_empty_message
                    FeedFilter.OnlyMe -> R.string.feed_my_empty_message
                    is FeedFilter.Friend -> R.string.feed_friend_empty_message
                }
                Text(stringResource(message), Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}

@Preview(name = "Feed - Light", widthDp = 406, heightDp = 780)
@Preview(name = "Feed - Dark", widthDp = 406, heightDp = 780, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Feed - Compact, large text", widthDp = 320, heightDp = 480, fontScale = 1.5f)
@Composable
private fun FeedPreview() {
    FeedPreviewContent()
}

@Preview(name = "Feed - Empty", widthDp = 406, heightDp = 780)
@Composable
private fun EmptyFeedPreview() {
    FeedPreviewContent(FeedUiState())
}

@Preview(name = "Feed - Loading", widthDp = 406, heightDp = 780)
@Composable
private fun LoadingFeedPreview() {
    FeedPreviewContent(FeedUiState(isLoading = true))
}

@Preview(name = "Feed - Error", widthDp = 406, heightDp = 780)
@Composable
private fun ErrorFeedPreview() {
    FeedPreviewContent(FeedUiState(error = R.string.feed_error))
}

@Preview(name = "Feed - Image error", widthDp = 406, heightDp = 780)
@Composable
private fun ImageErrorFeedPreview() {
    MomentoTheme(dynamicColor = false) {
        FeedScreen(state = previewFeedState().let { state ->
            state.copy(items = state.items.map { it.copy(moment = it.moment.copy(imageUrl = "")) })
        })
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun FeedPreviewContent(initialState: FeedUiState = previewFeedState()) {
    var filter by remember { mutableStateOf(initialState.filter) }
    // Preview-only color image; the app never inserts sample moments into the real feed.
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler {
        ColorImage(0xFF94745A.toInt())
    }) {
        MomentoTheme(dynamicColor = false) {
            FeedScreen(state = initialState.copy(filter = filter),
                actions = FeedActions(onFilterSelected = { filter = it }))
        }
    }
}

private fun previewFeedState(): FeedUiState {
    val owner = MomentOwner("emma", "emma", "Emma với tên hiển thị rất dài", null)
    val moment = Moment("preview", owner.id, "preview-photo", "Mẻ bánh sáng nay vừa ra lò! 🥐",
        AudienceType.ALL_FRIENDS, null, Instant.now().minusSeconds(180))
    return FeedUiState(
        items = listOf(FeedItem(moment, owner, locationName = "Le Petit Four Bakery"),
            FeedItem(moment.copy(id = "preview-2", caption = null, createdAt = null,
                audienceType = AudienceType.CUSTOM), owner)),
        friends = listOf(owner, MomentOwner("maya", "maya", "Maya", null)),
    )
}
