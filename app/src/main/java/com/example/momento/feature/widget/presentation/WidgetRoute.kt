package com.example.momento.feature.widget.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WidgetRoute(
    onBack: () -> Unit,
    onFriendsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler {
        if (!state.isSaving) {
            onBack()
        }
    }

    WidgetScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onNextPhoto = viewModel::nextPhoto,
        onSelectFriend = viewModel::selectFriend,
        onFriendsClick = onFriendsClick,
        modifier = modifier,
    )
}
