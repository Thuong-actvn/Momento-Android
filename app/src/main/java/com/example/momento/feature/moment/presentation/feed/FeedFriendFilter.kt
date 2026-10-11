package com.example.momento.feature.moment.presentation.feed

import android.graphics.Rect
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.MomentOwner
import com.example.momento.ui.components.MomentoAvatar

@Composable
fun FeedFriendFilter(
    friends: List<MomentOwner>,
    selectedFilter: FeedFilter,
    onSelect: (FeedFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val view = LocalView.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    var availableHeightPx by remember {
        mutableIntStateOf(with(density) { 300.dp.roundToPx() })
    }
    val menuMaxHeight = minOf(300.dp, with(density) { availableHeightPx.toDp() })
    val menuPosition = remember(gapPx) { FeedFilterMenuPositionProvider(gapPx) }

    Box(modifier = modifier) {
        FeedFilterButton(
            friends = friends,
            selectedFilter = selectedFilter,
            expanded = expanded,
            onClick = { expanded = !expanded },
            modifier = Modifier.onGloballyPositioned { coordinates ->
                val visibleWindow = Rect()
                view.getWindowVisibleDisplayFrame(visibleWindow)
                val buttonTop = coordinates.positionOnScreen().y
                availableHeightPx = (buttonTop - visibleWindow.top - gapPx)
                    .toInt()
                    .coerceAtLeast(1)
            },
        )

        if (expanded) {
            FeedFilterMenu(
                friends = friends,
                selectedFilter = selectedFilter,
                positionProvider = menuPosition,
                maxHeight = menuMaxHeight,
                onDismiss = { expanded = false },
                onSelect = { filter ->
                    expanded = false
                    onSelect(filter)
                },
            )
        }
    }
}

@Composable
private fun FeedFilterButton(
    friends: List<MomentOwner>,
    selectedFilter: FeedFilter,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val label = when (selectedFilter) {
        FeedFilter.All -> stringResource(R.string.feed_all_friends)
        FeedFilter.OnlyMe -> stringResource(R.string.feed_only_me)
        is FeedFilter.Friend -> {
            val friend = friends.find { it.id == selectedFilter.id }
            feedOwnerName(friend)
        }
    }
    val leadingIcon = if (selectedFilter == FeedFilter.OnlyMe) {
        Icons.Outlined.Lock
    } else {
        Icons.Outlined.Groups
    }
    val arrowIcon = if (expanded) {
        Icons.Outlined.KeyboardArrowDown
    } else {
        Icons.Outlined.KeyboardArrowUp
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .widthIn(max = 300.dp)
            .testTag("feed_filter"),
        shape = CircleShape,
        color = colors.surfaceContainer,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.primary,
            )
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = arrowIcon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun FeedFilterMenu(
    friends: List<MomentOwner>,
    selectedFilter: FeedFilter,
    positionProvider: PopupPositionProvider,
    maxHeight: Dp,
    onDismiss: () -> Unit,
    onSelect: (FeedFilter) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            shadowElevation = 8.dp,
            color = colors.surfaceContainerLowest,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(220.dp)
                    .heightIn(max = maxHeight)
                    .padding(4.dp)
                    .selectableGroup()
                    .testTag("feed_filter_menu"),
            ) {
                item {
                    FeedFilterOption(
                        label = stringResource(R.string.feed_all_friends),
                        selected = selectedFilter == FeedFilter.All,
                        onClick = { onSelect(FeedFilter.All) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Groups,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                item {
                    FeedFilterOption(
                        label = stringResource(R.string.feed_only_me),
                        selected = selectedFilter == FeedFilter.OnlyMe,
                        onClick = { onSelect(FeedFilter.OnlyMe) },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                items(friends, key = { it.id }) { friend ->
                    val filter = FeedFilter.Friend(friend.id)
                    FeedFilterOption(
                        label = feedOwnerName(friend),
                        selected = selectedFilter == filter,
                        onClick = { onSelect(filter) },
                    ) {
                        MomentoAvatar(
                            imageUrl = friend.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedFilterOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (selected) colors.primary else colors.onSurface
    val backgroundColor = if (selected) colors.primary.copy(alpha = 0.09f) else Color.Transparent

    Surface(
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .selectable(selected, onClick = onClick, role = Role.RadioButton)
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                leadingContent()
            }
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Box(
                modifier = Modifier.size(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// Center the menu above the button and keep it within the screen's horizontal bounds.
private class FeedFilterMenuPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val centeredX = anchorBounds.center.x - popupContentSize.width / 2
        val maximumX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val menuX = centeredX.coerceIn(0, maximumX)
        val menuY = anchorBounds.top - popupContentSize.height - gapPx

        return IntOffset(x = menuX, y = menuY)
    }
}
