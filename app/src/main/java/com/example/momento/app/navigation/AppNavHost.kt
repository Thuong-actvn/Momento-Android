package com.example.momento.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.momento.app.main.MainRoute
import com.example.momento.feature.auth.navigation.AuthGraph
import com.example.momento.feature.auth.navigation.CompleteProfile
import com.example.momento.feature.auth.navigation.authGraph
import com.example.momento.feature.chat.presentation.list.ChatListRoute
import com.example.momento.feature.chat.presentation.list.ChatListViewModel
import com.example.momento.feature.chat.presentation.thread.ChatThreadRoute
import com.example.momento.feature.friend.presentation.FriendRoute
import com.example.momento.feature.profile.presentation.editprofile.EditProfileRoute
import com.example.momento.feature.profile.presentation.profile.ProfileRoute

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
                MainRoute(
                    onExit = onExit,
                    onAvatarClick = {
                        navController.navigate(Profile) { launchSingleTop = true }
                    },
                    onMessagesClick = {
                        navController.navigate(ChatList) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<Profile> {
                ProfileRoute(
                    onBack = onBack,
                    onEditProfile = {
                        navController.navigate(EditProfile) { launchSingleTop = true }
                    },
                    onConnectionsClick = {
                        navController.navigate(Friends) { launchSingleTop = true }
                    },
                    onSignedOut = {
                        navController.navigate(AuthGraph) {
                            popUpTo(MainGraph) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<EditProfile> {
                EditProfileRoute(onBack = onBack)
            }
            composable<Friends> {
                FriendRoute(onBack = onBack)
            }
            composable<ChatList> {
                ChatListRoute(
                    onBack = onBack,
                    onOpenConversation = { userId ->
                        navController.navigate(ChatThread(otherUserId = userId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<ChatThread> { entry ->
                val chatRoute = entry.toRoute<ChatThread>()

                // Dùng lại ViewModel danh sách để lấy tên/avatar cho khung chat.
                val listBackStackEntry = remember(entry) {
                    navController.getBackStackEntry<ChatList>()
                }
                val chatListViewModel = hiltViewModel<ChatListViewModel>(listBackStackEntry)

                ChatThreadRoute(
                    otherUserId = chatRoute.otherUserId,
                    onBack = onBack,
                    listViewModel = chatListViewModel,
                )
            }
        }
    }
    // Main and Profile own Back for tab selection and sign-out respectively.
    BackHandler(
        enabled = backStackEntry?.destination?.hierarchy?.any { it.hasRoute<AuthGraph>() } == true,
        onBack = onBack,
    )
}
