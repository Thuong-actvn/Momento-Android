package com.example.momento.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.momento.feature.auth.presentation.completeprofile.CompleteProfileScreen
import com.example.momento.feature.auth.presentation.login.LoginScreen
import com.example.momento.feature.auth.presentation.otp.OtpScreen

fun NavGraphBuilder.authGraph(
    onProfileRequired: () -> Unit,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    navigation<AuthGraph>(startDestination = Login) {
        composable<Login> {
            LoginScreen(
                onProfileRequired = onProfileRequired,
                onAuthSuccess = onAuthSuccess,
            )
        }
        composable<Otp> {
            OtpScreen(
                onBack = onBack,
                onProfileRequired = onProfileRequired,
                onAuthSuccess = onAuthSuccess,
            )
        }
        composable<CompleteProfile> {
            CompleteProfileScreen(onProfileCompleted = onAuthSuccess)
        }
    }
}
