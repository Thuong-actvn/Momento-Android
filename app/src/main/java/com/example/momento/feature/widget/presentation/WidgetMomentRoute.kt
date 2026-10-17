package com.example.momento.feature.widget.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R
import com.example.momento.feature.moment.presentation.journal.JournalDetailScreen
import com.example.momento.feature.moment.presentation.journal.JournalDetailUiState
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoTopBar

@Composable
fun WidgetMomentRoute(
    photoId: String,
    onBack: () -> Unit,
    viewModel: WidgetMomentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(photoId) {
        viewModel.load(photoId)
    }
    val item = state.item
    if (item != null) {
        JournalDetailScreen(
            state = JournalDetailUiState(item = item),
            friends = emptyList(),
            onClose = onBack,
            onLoadReactions = {},
            onRequestDelete = {},
            onDismissDelete = {},
            onDelete = {},
            backContentDescription = stringResource(R.string.widget_back),
        )
        return
    }

    Scaffold(
        topBar = {
            MomentoTopBar(
                title = stringResource(R.string.journal_detail_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.widget_back),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
            }
            state.error?.let { error ->
                MomentoRetryMessage(
                    message = stringResource(error),
                    actionText = stringResource(R.string.widget_retry),
                    onRetry = { viewModel.load(photoId) },
                )
            }
        }
    }
}
