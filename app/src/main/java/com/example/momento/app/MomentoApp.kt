package com.example.momento.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.momento.app.navigation.AppNavHost
import com.example.momento.app.navigation.SessionStartupState
import com.example.momento.app.navigation.SessionStartupViewModel

@Composable
fun MomentoApp(
    onExit: () -> Unit,
    onStartupContentReady: () -> Unit,
    widgetPhotoId: String? = null,
    onWidgetPhotoOpened: () -> Unit = {},
) {
    val startup: SessionStartupViewModel = hiltViewModel()
    val state by startup.state.collectAsStateWithLifecycle()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        when (val session = state) {
            SessionStartupState.Checking -> Unit
            is SessionStartupState.Ready -> {
                SideEffect(onStartupContentReady)
                // Restore navigation only for the confirmed account and profile state.
                key(session.user?.id, session.user?.isProfileCompleted) {
                    AppNavHost(
                        navController = rememberNavController(),
                        onExit = onExit,
                        widgetPhotoId = widgetPhotoId,
                        onWidgetPhotoOpened = onWidgetPhotoOpened,
                        startInMain = session.user?.isProfileCompleted == true,
                        needsProfile = session.user?.isProfileCompleted == false,
                        restoreFailed = session.restoreFailed,
                        onRetryRestore = startup::restore,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
