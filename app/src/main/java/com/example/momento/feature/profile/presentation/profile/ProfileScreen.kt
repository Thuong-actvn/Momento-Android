package com.example.momento.feature.profile.presentation.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.momento.BuildConfig
import com.example.momento.R
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.profile.presentation.components.ProfileConnectionsCard
import com.example.momento.feature.profile.presentation.components.ProfileIdentityCard
import com.example.momento.feature.profile.presentation.components.ProfileSettingsCard
import com.example.momento.ui.components.MomentoActionButton
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoTopBar
import com.example.momento.ui.theme.MomentoTheme

@Composable
fun ProfileScreen(
    user: CurrentUser?,
    state: ProfileUiState,
    onBack: () -> Unit,
    onEditProfile: () -> Unit,
    onConnectionsClick: () -> Unit,
    onSignOut: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val canNavigate = !state.isSigningOut && !state.isSignedOut

    Scaffold(
        modifier = modifier,
        containerColor = colors.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            MomentoTopBar(
                title = stringResource(R.string.profile_screen_title),
                onBack = onBack,
                backContentDescription = stringResource(R.string.profile_back),
                enabled = canNavigate,
                isLoading = state.isRefreshing,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileIdentityCard(
                user = user,
                onEditProfile = onEditProfile,
                enabled = canNavigate && user != null,
            )
            state.error?.let { error ->
                MomentoRetryMessage(
                    message = stringResource(error),
                    actionText = stringResource(R.string.profile_retry),
                    onRetry = onRetry,
                    enabled = canNavigate && !state.isRefreshing,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            ProfileConnectionsCard(onClick = onConnectionsClick, enabled = canNavigate)
            Spacer(Modifier.height(12.dp))
            ProfileSettingsCard()
            Spacer(Modifier.height(20.dp))
            MomentoActionButton(
                text = stringResource(
                    if (state.isSigningOut) R.string.profile_logging_out else R.string.profile_logout,
                ),
                leadingIcon = Icons.AutoMirrored.Outlined.Logout,
                contentColor = colors.error,
                isLoading = state.isSigningOut,
                enabled = canNavigate,
                onClick = onSignOut,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(
                    R.string.profile_app_version,
                    stringResource(R.string.app_name),
                    BuildConfig.VERSION_NAME,
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun ProfilePreview() {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        ProfileScreen(
            user = CurrentUser(
                id = "preview",
                username = "alexm",
                displayName = "Alex Morgan",
                avatarUrl = null,
                email = null,
                phoneNumber = null,
                isProfileCompleted = true,
            ),
            state = ProfileUiState(),
            onBack = {},
            onEditProfile = {},
            onConnectionsClick = {},
            onSignOut = {},
            onRetry = {},
        )
    }
}
