package com.example.momento.feature.moment.presentation.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun JournalRoute(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        JournalScreen(modifier = modifier)
    } else {
        JournalRouteContent(viewModel = hiltViewModel(), modifier = modifier)
    }
}

@Composable
fun JournalRouteContent(
    viewModel: JournalViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    JournalScreen(
        modifier = modifier,
        state = state,
        onFilterSelected = viewModel::selectFilter,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRetryFriends = viewModel::retryFriends,
    )
}
