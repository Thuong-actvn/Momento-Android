package com.example.momento.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import com.example.momento.app.main.MainRoute
import com.example.momento.feature.auth.navigation.AuthGraph
import com.example.momento.feature.auth.navigation.CompleteProfile
import com.example.momento.feature.auth.navigation.authGraph

@Composable
fun AppNavHost(
    navController: NavHostController,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val onBack = {
        if (navController.previousBackStackEntry == null || !navController.popBackStack()) onExit()
    }
    NavHost(
        navController = navController,
        startDestination = AuthGraph,
        modifier = modifier,
    ) {
        authGraph(
            onProfileRequired = {
                navController.navigate(CompleteProfile) {
                    // Keep AuthGraph, but remove Login from its stack.
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
        navigation<MainGraph>(startDestination = MainTabs) {
            composable<MainTabs> {
                MainRoute(onExit = onExit)
            }
        }
    }
    // Main owns Back so switching tabs never changes the app's back stack.
    BackHandler(
        enabled = backStackEntry?.destination?.hasRoute<MainTabs>() != true,
        onBack = onBack,
    )
}
