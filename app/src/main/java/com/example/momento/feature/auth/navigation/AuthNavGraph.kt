package com.example.momento.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.momento.feature.auth.presentation.completeprofile.CompleteProfileScreen
import com.example.momento.feature.auth.presentation.login.LoginScreen

fun NavGraphBuilder.authGraph(
    needsProfile: Boolean,
    restoreFailed: Boolean,
    onRetryRestore: () -> Unit,
    onProfileRequired: () -> Unit,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    navigation<AuthGraph>(startDestination = if (needsProfile) CompleteProfile else Login) {
        composable<Login> {
            LoginScreen(
                onProfileRequired = onProfileRequired,
                onAuthSuccess = onAuthSuccess,
                restoreFailed = restoreFailed,
                onRetryRestore = onRetryRestore,
            )
        }
        composable<CompleteProfile> {
            CompleteProfileScreen(
                onProfileCompleted = onAuthSuccess,
                onBack = onBack,
            )
        }
    }
}
