package com.example.momento.feature.widget.presentation

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R
import com.example.momento.feature.widget.presentation.home.MomentoWidgetProvider
import kotlinx.coroutines.launch

@Composable
fun WidgetRoute(
    onBack: () -> Unit,
    onFriendsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val widgetManager = remember(context) { AppWidgetManager.getInstance(context) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val manualAddMessage = stringResource(R.string.widget_add_manually)

    BackHandler {
        if (!state.isSaving) {
            onBack()
        }
    }

    WidgetScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::loadData,
        onNextPhoto = viewModel::nextPhoto,
        onSelectFriend = viewModel::selectFriend,
        onFriendsClick = onFriendsClick,
        onAddWidget = {
            val provider = ComponentName(context, MomentoWidgetProvider::class.java)
            val requestAccepted = widgetManager.isRequestPinAppWidgetSupported &&
                widgetManager.requestPinAppWidget(provider, null, null)
            if (!requestAccepted) {
                scope.launch {
                    snackbar.showSnackbar(manualAddMessage)
                }
            }
        },
        snackbar = snackbar,
        modifier = modifier,
    )
}
