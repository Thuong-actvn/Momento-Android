package com.example.momento.feature.profile.presentation.profile

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ProfileRoute(
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onConnectionsClick: () -> Unit,
    onWidgetsClick: () -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val user by viewModel.currentUser.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isSignedOut) {
        if (state.isSignedOut) onSignedOut()
    }
    BackHandler {
        if (!state.isSigningOut && !state.isSignedOut) onBack()
    }

    ProfileScreen(
        user = user,
        state = state,
        onBack = onBack,
        onEditProfile = onEditProfile,
        onConnectionsClick = onConnectionsClick,
        onWidgetsClick = onWidgetsClick,
        onSignOut = viewModel::signOut,
        onRetry = viewModel::refreshProfile,
        modifier = modifier,
    )
}
