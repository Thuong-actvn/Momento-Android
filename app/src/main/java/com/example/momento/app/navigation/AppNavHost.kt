package com.example.momento.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.example.momento.feature.auth.navigation.AuthGraph
import com.example.momento.feature.auth.navigation.CompleteProfile
import com.example.momento.feature.auth.navigation.Otp
import com.example.momento.feature.auth.navigation.authGraph
import com.example.momento.feature.moment.navigation.MomentFeed
import com.example.momento.feature.moment.presentation.feed.MomentFeedScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBack = {
        if (navController.previousBackStackEntry == null || !navController.popBackStack()) onExit()
    }
    NavHost(
        navController = navController,
        startDestination = AuthGraph,
        modifier = modifier,
    ) {
        authGraph(
            onOtpSent = { navController.navigate(Otp) { launchSingleTop = true } },
            onProfileRequired = {
                navController.navigate(CompleteProfile) {
                    // Keep AuthGraph, but remove Login/OTP from its stack.
                    popUpTo(AuthGraph) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onAuthSuccess = {
                navController.navigate(MainGraph) {
                    popUpTo(AuthGraph) { inclusive = true }
                    launchSingleTop = true
                }
            },
            onBack = onBack,
        )
        navigation<MainGraph>(startDestination = MomentFeed) {
            composable<MomentFeed> {
                MomentFeedScreen()
            }
        }
    }
    // With no previous screen, exit the Activity; OTP pops back to Login.
    BackHandler(onBack = onBack)
}
