package com.example.momento.app.main

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.feature.moment.presentation.camera.CameraRoute
import com.example.momento.feature.moment.presentation.feed.FeedRoute
import com.example.momento.ui.theme.MomentoTheme

enum class MainTab {
    FEED,
    CAMERA,
    JOURNAL,
}

@Composable
fun MainScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.FEED) }

    BackHandler {
        if (selectedTab == MainTab.FEED) {
            onExit()
        } else {
            selectedTab = MainTab.FEED
        }
    }

    MainScreenContent(
        selectedTab = selectedTab,
        onTabSelected = { selectedTab = it },
        modifier = modifier,
        avatarUrl = avatarUrl,
        onAvatarClick = onAvatarClick,
    )
}

@Composable
fun MainScreenContent(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    onAvatarClick: (() -> Unit)? = null,
) {
    val tabStateHolder = rememberSaveableStateHolder()
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            if (selectedTab != MainTab.CAMERA) {
                MainNavigationBar(
                    selectedTab = selectedTab,
                    onTabSelected = onTabSelected,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            tabStateHolder.SaveableStateProvider(selectedTab) {
                when (selectedTab) {
                    MainTab.FEED -> FeedRoute(
                        modifier = Modifier.fillMaxSize(),
                        avatarUrl = avatarUrl,
                        onAvatarClick = onAvatarClick,
                    )
                    MainTab.CAMERA -> CameraRoute(
                        modifier = Modifier.fillMaxSize(),
                        ownAvatarUrl = avatarUrl,
                        onClose = { onTabSelected(MainTab.FEED) },
                    )
                    MainTab.JOURNAL -> JournalPlaceholder(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
private fun MainNavigationBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cameraLabel = stringResource(R.string.main_tab_camera)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                ),
            )
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                .clip(RoundedCornerShape(36.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .heightIn(min = 72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledTab(
                selected = selectedTab == MainTab.FEED,
                onClick = { onTabSelected(MainTab.FEED) },
                label = stringResource(R.string.main_tab_moments),
                iconRes = R.drawable.ic_main_moments,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(56.dp))
            LabeledTab(
                selected = selectedTab == MainTab.JOURNAL,
                onClick = { onTabSelected(MainTab.JOURNAL) },
                label = stringResource(R.string.main_tab_journal),
                iconRes = R.drawable.ic_main_journal,
                modifier = Modifier.weight(1f),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-8).dp)
                .size(56.dp)
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 20.dp,
                        spread = (-4).dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                        offset = DpOffset(x = 0.dp, y = 12.dp),
                    ),
                )
                .dropShadow(
                    shape = CircleShape,
                    shadow = Shadow(
                        radius = 8.dp,
                        spread = (-2).dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        offset = DpOffset(x = 0.dp, y = 4.dp),
                    ),
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .selectable(
                    selected = selectedTab == MainTab.CAMERA,
                    interactionSource = null,
                    indication = null,
                    onClick = { onTabSelected(MainTab.CAMERA) },
                    role = Role.Tab,
                )
                .semantics { contentDescription = cameraLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_main_camera),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun LabeledTab(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = color,
        )
        Text(
            text = label,
            modifier = Modifier.padding(top = 2.dp),
            color = color,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun JournalPlaceholder(modifier: Modifier = Modifier) {
    TabPlaceholder(
        title = stringResource(R.string.main_journal_placeholder),
        modifier = modifier,
    )
}

@Composable
private fun TabPlaceholder(title: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showSystemUi = true)
@Composable
private fun MainFeedPreview() {
    val activity = LocalActivity.current
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        MainScreen(onExit = { activity?.finish() })
    }
}

@Composable
private fun MainPreview(selectedTab: MainTab) {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        MainScreenContent(selectedTab = selectedTab, onTabSelected = {})
    }
}
