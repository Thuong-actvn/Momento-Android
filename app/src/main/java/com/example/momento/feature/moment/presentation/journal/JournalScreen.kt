package com.example.momento.feature.moment.presentation.journal

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.feature.moment.presentation.components.MOMENT_IMAGE_ASPECT_RATIO

sealed interface JournalFilter {
    data object All : JournalFilter
    data object OnlyMe : JournalFilter
    data class Friend(val id: String) : JournalFilter
}

data class JournalItem(
    val moment: Moment,
    val owner: MomentOwner? = null,
    val isOwn: Boolean = false,
)

data class JournalSection(
    val id: String,
    val title: String,
    val items: List<JournalItem>,
    val highlighted: Boolean = false,
)

data class JournalUiState(
    val sections: List<JournalSection> = emptyList(),
    val friends: List<MomentOwner> = emptyList(),
    val filter: JournalFilter = JournalFilter.All,
    val isLoading: Boolean = false,
    @param:StringRes val error: Int? = null,
)

@Composable
fun JournalRoute(modifier: Modifier = Modifier) {
    // Until API integration, only the two fixed filters are available in the app.
    var onlyMe by rememberSaveable { mutableStateOf(false) }

    JournalScreen(
        modifier = modifier,
        state = JournalUiState(
            filter = if (onlyMe) JournalFilter.OnlyMe else JournalFilter.All,
        ),
        onFilterSelected = { onlyMe = it == JournalFilter.OnlyMe },
    )
}

@Composable
fun JournalScreen(
    modifier: Modifier = Modifier,
    state: JournalUiState = JournalUiState(),
    onFilterSelected: (JournalFilter) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            JournalTopBar()
            JournalFilters(
                friends = state.friends,
                selectedFilter = state.filter,
                onSelect = onFilterSelected,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (state.sections.any { it.items.isNotEmpty() }) {
                    key(state.filter) {
                        JournalTimeline(sections = state.sections)
                    }
                } else {
                    JournalStatus(state = state, onRetry = onRetry)
                }
            }
        }
    }
}

@Composable
private fun JournalTimeline(sections: List<JournalSection>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
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
                JournalMomentRow(section = section)
            }
        }
    }
}

@Composable
private fun JournalMomentRow(section: JournalSection) {
    val rowState = rememberLazyListState()
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
private fun JournalStatus(state: JournalUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(24.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.testTag("journal_loading"))
                Text(
                    text = stringResource(R.string.journal_loading),
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
                TextButton(onClick = onRetry, modifier = Modifier.testTag("journal_retry")) {
                    Text(stringResource(R.string.journal_retry))
                }
            }
            else -> {
                Text(
                    text = stringResource(R.string.journal_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                val message = when (state.filter) {
                    JournalFilter.All -> R.string.journal_empty_message
                    JournalFilter.OnlyMe -> R.string.journal_my_empty_message
                    is JournalFilter.Friend -> R.string.journal_friend_empty_message
                }
                Text(
                    text = stringResource(message),
                    modifier = Modifier.testTag("journal_empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
