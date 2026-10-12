package com.example.momento.feature.moment.presentation.feed

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.theme.MomentoTheme
import java.time.Instant

const val FEED_HEART_EMOJI = "💛"
val feedReactionEmojis = listOf(FEED_HEART_EMOJI, "😂", "😍", "😮", "🔥")

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
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    @param:StringRes val loadMoreError: Int? = null,
    val visibleMomentId: String? = null,
    // Reset the pager only after a new first page succeeds.
    val pageVersion: Int = 0,
    val isLoadingFriends: Boolean = false,
    @param:StringRes val friendsError: Int? = null,
    val sendingReactionId: String? = null,
    val sentReactions: Map<String, String> = emptyMap(),
    val message: FeedMessage? = null,
)

// Each instance represents a new message, even when the same error occurs again.
class FeedMessage(
    @param:StringRes val text: Int,
    val canRefresh: Boolean = false,
)

data class FeedActions(
    val onRetry: () -> Unit = {},
    val onFilterSelected: (FeedFilter) -> Unit = {},
    val onReact: (momentId: String, emoji: String) -> Unit = { _, _ -> },
    val onRetryFriends: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetryLoadMore: () -> Unit = {},
    val onMomentVisible: (String) -> Unit = {},
)

@Composable
fun FeedScreen(
    modifier: Modifier = Modifier,
    state: FeedUiState = FeedUiState(),
    actions: FeedActions = FeedActions(),
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FeedTopBar(avatarUrl = avatarUrl, onAvatarClick = onAvatarClick)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.items.isNotEmpty()) {
                    key(state.filter, state.pageVersion) {
                        FeedPager(
                            items = state.items,
                            initialMomentId = state.visibleMomentId,
                            actions = actions,
                            reactionsEnabled = state.sendingReactionId == null,
                            sentReactions = state.sentReactions,
                        )
                    }
                } else {
                    FeedStatus(state, actions.onRetry)
                }
            }
            if (state.items.isNotEmpty()) {
                FeedPageStatus(state = state, actions = actions)
            }
            if (state.friendsError != null) {
                MomentoRetryMessage(
                    message = stringResource(state.friendsError),
                    actionText = stringResource(
                        if (state.isLoadingFriends) R.string.feed_loading_friends else R.string.feed_retry,
                    ),
                    onRetry = actions.onRetryFriends,
                    enabled = !state.isLoadingFriends,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .testTag("feed_friends_error"),
                )
            }
            FeedFilterRow(state = state, actions = actions)
            DisabledMessageComposer(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FeedPageStatus(state: FeedUiState, actions: FeedActions) {
    when {
        state.error != null || state.loadMoreError != null -> {
            val error = state.error ?: state.loadMoreError ?: return
            MomentoRetryMessage(
                message = stringResource(error),
                actionText = stringResource(R.string.feed_retry),
                onRetry = if (state.error != null) actions.onRefresh else actions.onRetryLoadMore,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .testTag("feed_page_error"),
            )
        }
        state.isLoadingMore -> {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag("feed_loading_more"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.feed_loading_more),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.visibleMomentId == state.items.last().moment.id && !state.isRefreshing -> {
            if (state.hasMore) {
                TextButton(onClick = actions.onLoadMore) {
                    Text(stringResource(R.string.feed_load_more))
                }
            } else {
                Text(
                    text = stringResource(R.string.feed_end),
                    modifier = Modifier.padding(top = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FeedFilterRow(state: FeedUiState, actions: FeedActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Equal space on both sides keeps the filter in the center.
        Spacer(modifier = Modifier.width(48.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FeedFriendFilter(
                friends = state.friends,
                selectedFilter = state.filter,
                onSelect = actions.onFilterSelected,
            )
        }
        IconButton(
            onClick = actions.onRefresh,
            enabled = !state.isLoading && !state.isRefreshing,
            modifier = Modifier
                .size(48.dp)
                .testTag("feed_refresh"),
        ) {
            Icon(
                imageVector = Icons.Outlined.VerticalAlignTop,
                contentDescription = stringResource(R.string.feed_back_to_top),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun FeedStatus(state: FeedUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.testTag("feed_loading"))
                Text(
                    text = stringResource(R.string.feed_loading),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            state.error != null -> {
                Text(
                    text = stringResource(state.error),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                TextButton(onClick = onRetry, modifier = Modifier.testTag("feed_retry")) {
                    Text(stringResource(R.string.feed_retry))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.feed_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                val message = when (state.filter) {
                    FeedFilter.All -> R.string.feed_empty_message
                    FeedFilter.OnlyMe -> R.string.feed_my_empty_message
                    is FeedFilter.Friend -> R.string.feed_friend_empty_message
                }
                Text(
                    text = stringResource(message),
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
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
