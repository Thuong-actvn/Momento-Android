package com.example.momento.feature.auth.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.momento.feature.auth.presentation.CompleteProfileScreen
import com.example.momento.feature.auth.presentation.LoginScreen
import com.example.momento.feature.auth.presentation.OtpScreen

fun NavGraphBuilder.authGraph(
    onOtpSent: () -> Unit,
    onProfileRequired: () -> Unit,
    onAuthSuccess: () -> Unit,
    onBack: () -> Unit,
) {
    navigation<AuthGraph>(startDestination = Login) {
        composable<Login> {
            LoginScreen(
                onOtpSent = onOtpSent,
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
