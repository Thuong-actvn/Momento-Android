package com.example.momento.feature.moment.presentation.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.MomentOwner

@Composable
fun JournalTopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.main_tab_journal),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.testTag("journal_notifications"),
        ) {
            Icon(
                imageVector = Icons.Outlined.Notifications,
                contentDescription = stringResource(R.string.journal_notifications_unavailable),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun JournalFilters(
    friends: List<MomentOwner>,
    selectedFilter: JournalFilter,
    onSelect: (JournalFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val friendColors = listOf(colors.primary, colors.secondary, colors.tertiary)

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .testTag("journal_filters"),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all") {
            JournalFilterChip(
                label = stringResource(R.string.journal_all),
                selected = selectedFilter == JournalFilter.All,
                onClick = { onSelect(JournalFilter.All) },
                modifier = Modifier.testTag("journal_filter_all"),
            )
        }
        item(key = "me") {
            JournalFilterChip(
                label = stringResource(R.string.journal_my_photos),
                selected = selectedFilter == JournalFilter.OnlyMe,
                onClick = { onSelect(JournalFilter.OnlyMe) },
                modifier = Modifier.testTag("journal_filter_me"),
            )
        }
        itemsIndexed(items = friends, key = { _, friend -> "friend_${friend.id}" }) { index, friend ->
            val selected = selectedFilter == JournalFilter.Friend(friend.id)

            JournalFilterChip(
                label = journalOwnerName(friend),
                selected = selected,
                onClick = { onSelect(JournalFilter.Friend(friend.id)) },
                modifier = Modifier.testTag("journal_filter_friend_${friend.id}"),
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (selected) {
                                    colors.onPrimary
                                } else {
                                    friendColors[index % friendColors.size]
                                },
                                shape = CircleShape,
                            ),
                    )
                },
            )
        }
    }
}

@Composable
private fun JournalFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.widthIn(max = 160.dp),
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = leadingIcon,
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surfaceContainer,
            labelColor = colors.onSurfaceVariant,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
        ),
    )
}

@Composable
fun JournalSectionHeader(section: JournalSection, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (section.highlighted) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        Text(
            text = section.title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = pluralStringResource(
                R.plurals.journal_moment_count,
                section.items.size,
                section.items.size,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun journalOwnerName(owner: MomentOwner?): String {
    return owner?.displayName?.takeIf { it.isNotBlank() }
        ?: owner?.username?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.journal_unknown_owner)
}
