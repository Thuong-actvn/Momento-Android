package com.example.momento.feature.moment.presentation.journal

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.presentation.components.MOMENT_IMAGE_ASPECT_RATIO
import com.example.momento.ui.components.MomentoRetryMessage

@Composable
fun JournalTimeline(
    sections: List<JournalSection>,
    state: JournalUiState,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val canLoadMore = state.hasMore && !state.isLoadingMore && !state.isRefreshing &&
        state.error == null && state.loadMoreError == null
    val paginationConnection = rememberJournalPaginationConnection(
        listState = listState,
        orientation = Orientation.Vertical,
        enabled = canLoadMore,
        onLoadMore = onLoadMore,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(paginationConnection)
            .testTag("journal_timeline"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        items(
            items = sections.filter { it.items.isNotEmpty() },
            key = { it.id },
        ) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JournalSectionHeader(
                    section = section,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                JournalMomentRow(
                    section = section,
                    canLoadMore = canLoadMore,
                    onLoadMore = onLoadMore,
                )
            }
        }
        if (state.isLoadingMore || state.loadMoreError != null) {
            item(key = "page_status") {
                JournalPageStatus(state = state, onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun JournalPageStatus(
    state: JournalUiState,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        val error = state.loadMoreError
        when {
            state.isLoadingMore -> {
                CircularProgressIndicator(modifier = Modifier.testTag("journal_loading_more"))
            }
            error != null -> {
                MomentoRetryMessage(
                    message = stringResource(error),
                    actionText = stringResource(R.string.journal_retry),
                    onRetry = onRetry,
                    enabled = !state.isRefreshing,
                    modifier = Modifier.testTag("journal_page_error"),
                )
            }
        }
    }
}

@Composable
private fun JournalMomentRow(
    section: JournalSection,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val rowState = rememberLazyListState()
    val paginationConnection = rememberJournalPaginationConnection(
        listState = rowState,
        orientation = Orientation.Horizontal,
        enabled = canLoadMore,
        onLoadMore = onLoadMore,
    )
    val flingBehavior = rememberSnapFlingBehavior(
        lazyListState = rowState,
        snapPosition = SnapPosition.Start,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val horizontalMargin = 20.dp
        val cardSpacing = 12.dp
        val shadowPadding = 4.dp
        val staggerOffset = 8.dp
        val cardWidth = (maxWidth - horizontalMargin * 2 - cardSpacing) / 2
        val cardHeight = cardWidth / MOMENT_IMAGE_ASPECT_RATIO
        val itemStepPx = with(LocalDensity.current) { (cardWidth + cardSpacing).toPx() }

        LazyRow(
            state = rowState,
            flingBehavior = flingBehavior,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalMargin - shadowPadding)
                .height(cardHeight + staggerOffset + shadowPadding * 2)
                .nestedScroll(paginationConnection)
                .testTag("journal_row_${section.id}"),
            // Leave room for shadows while clipping the cards outside the two visible slots.
            contentPadding = PaddingValues(
                start = shadowPadding,
                end = shadowPadding,
                top = shadowPadding,
                bottom = shadowPadding + staggerOffset,
            ),
            horizontalArrangement = Arrangement.spacedBy(cardSpacing),
        ) {
            itemsIndexed(
                items = section.items,
                key = { _, item -> item.moment.id },
            ) { index, item ->
                JournalCard(
                    item = item,
                    modifier = Modifier
                        .width(cardWidth)
                        .graphicsLayer {
                            // Height follows the visible slot, so the next card rises as it moves left.
                            val scrollFraction = rowState.firstVisibleItemScrollOffset / itemStepPx
                            val slot = index - rowState.firstVisibleItemIndex - scrollFraction
                            translationY = staggerOffset.toPx() * slot.coerceIn(0f, 1f)
                        },
                )
            }
        }
    }
}

@Composable
private fun rememberJournalPaginationConnection(
    listState: LazyListState,
    orientation: Orientation,
    enabled: Boolean,
    onLoadMore: () -> Unit,
): NestedScrollConnection {
    val canLoadMore by rememberUpdatedState(enabled)
    val loadMore by rememberUpdatedState(onLoadMore)

    return remember(listState, orientation) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val scroll = if (orientation == Orientation.Horizontal) {
                    consumed.x + available.x
                } else {
                    consumed.y + available.y
                }

                // Only load after a forward scroll, including a swipe on a short list.
                if (!canLoadMore || scroll >= 0f) return Offset.Zero

                val layout = listState.layoutInfo
                val lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index
                    ?: return Offset.Zero
                val threshold = if (orientation == Orientation.Horizontal) 2 else 1
                val isNearEnd = lastVisibleIndex >= layout.totalItemsCount - threshold

                if (isNearEnd) {
                    loadMore()
                }
                return Offset.Zero
            }
        }
    }
}
