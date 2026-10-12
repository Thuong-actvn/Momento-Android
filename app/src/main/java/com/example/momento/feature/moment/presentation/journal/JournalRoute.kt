package com.example.momento.feature.moment.presentation.journal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun JournalRoute(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        JournalScreen(modifier = modifier)
    } else {
        JournalRouteContent(
            viewModel = hiltViewModel(),
            detailViewModel = hiltViewModel(),
            modifier = modifier,
        )
    }
}

@Composable
fun JournalRouteContent(
    viewModel: JournalViewModel,
    modifier: Modifier = Modifier,
    detailViewModel: JournalDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail by detailViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(detail.deletedMomentId) {
        val deletedId = detail.deletedMomentId ?: return@LaunchedEffect
        viewModel.removeMoment(deletedId)
        detailViewModel.consumeDeletedMoment(deletedId)
    }

    JournalScreen(
        modifier = modifier,
        state = state,
        onFilterSelected = viewModel::selectFilter,
        onRetry = viewModel::retry,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRetryFriends = viewModel::retryFriends,
        onMomentClick = detailViewModel::open,
    )

    if (detail.item != null) {
        Dialog(
            onDismissRequest = detailViewModel::close,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !detail.isDeleting,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            JournalDetailScreen(
                state = detail,
                friends = state.friends,
                onClose = detailViewModel::close,
                onLoadReactions = detailViewModel::loadReactions,
                onRequestDelete = detailViewModel::requestDelete,
                onDismissDelete = detailViewModel::dismissDeleteConfirmation,
                onDelete = detailViewModel::delete,
            )
        }
    }
}
