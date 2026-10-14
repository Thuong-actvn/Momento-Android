package com.example.momento.feature.chat.presentation.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoEmptyState

@Composable
fun ChatListLoading() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp)
            .testTag("chat_loading"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.chat_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

@Composable
fun ChatListEmpty(searching: Boolean) {
    val testTag: String
    val icon: ImageVector
    val title: Int
    val description: Int

    if (searching) {
        testTag = "chat_search_empty"
        icon = Icons.Outlined.Search
        title = R.string.chat_search_empty_title
        description = R.string.chat_search_empty_description
    } else {
        testTag = "chat_empty"
        icon = Icons.Outlined.ChatBubbleOutline
        title = R.string.chat_empty_title
        description = R.string.chat_empty_description
    }

    MomentoEmptyState(
        icon = icon,
        title = stringResource(title),
        description = stringResource(description),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 56.dp)
            .testTag(testTag),
        iconSize = 44.dp,
    )
}
