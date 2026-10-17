package com.example.momento.app.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.example.momento.app.main.MainRoute
import com.example.momento.app.main.MainViewModel
import com.example.momento.feature.auth.navigation.AuthGraph
import com.example.momento.feature.auth.navigation.CompleteProfile
import com.example.momento.feature.auth.navigation.authGraph
import com.example.momento.feature.chat.presentation.list.ChatListRoute
import com.example.momento.feature.chat.presentation.list.ChatListViewModel
import com.example.momento.feature.chat.presentation.detail.ChatDetailRoute
import com.example.momento.feature.friend.presentation.FriendRoute
import com.example.momento.feature.profile.presentation.editprofile.EditProfileRoute
import com.example.momento.feature.profile.presentation.profile.ProfileRoute
import com.example.momento.feature.widget.presentation.WidgetRoute
import com.example.momento.feature.widget.presentation.WidgetMomentRoute

@Composable
fun AppNavHost(
    navController: NavHostController,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    widgetPhotoId: String? = null,
    onWidgetPhotoOpened: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val sessionViewModel: MainViewModel = hiltViewModel()
    val currentUser by sessionViewModel.currentUser.collectAsStateWithLifecycle()
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
                    onOpenConversation = { userId ->
                        navController.navigate(ChatDetail(otherUserId = userId)) {
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
                    onWidgetsClick = {
                        navController.navigate(WidgetSettings) {
                            launchSingleTop = true
                        }
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
            composable<WidgetSettings> {
                WidgetRoute(
                    onBack = onBack,
                    onFriendsClick = {
                        navController.navigate(Friends) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<WidgetMoment> { entry ->
                val route = entry.toRoute<WidgetMoment>()
                WidgetMomentRoute(photoId = route.photoId, onBack = onBack)
            }
            composable<ChatList> { entry ->
                val mainEntry = remember(entry) { navController.getBackStackEntry<MainGraph>() }
                val listViewModel = hiltViewModel<ChatListViewModel>(mainEntry)
                ChatListRoute(
                    onBack = onBack,
                    onOpenConversation = { userId ->
                        navController.navigate(ChatDetail(otherUserId = userId)) {
                            launchSingleTop = true
                        }
                    },
                    viewModel = listViewModel,
                )
            }
            composable<ChatDetail> { entry ->
                val chatRoute = entry.toRoute<ChatDetail>()

                // Cùng nguồn tên/avatar khi mở từ danh sách hoặc trực tiếp từ feed.
                val mainEntry = remember(entry) { navController.getBackStackEntry<MainGraph>() }
                val chatListViewModel = hiltViewModel<ChatListViewModel>(mainEntry)

                ChatDetailRoute(
                    otherUserId = chatRoute.otherUserId,
                    onBack = onBack,
                    listViewModel = chatListViewModel,
                )
            }
        }
    }
    // Wait for session restoration/login before requesting the widget's photo.
    LaunchedEffect(widgetPhotoId, backStackEntry, currentUser) {
        val photoId = widgetPhotoId
        val inMainGraph = backStackEntry?.destination?.hierarchy
            ?.any { it.hasRoute<MainGraph>() } == true
        if (photoId.isNullOrBlank() || !inMainGraph) {
            return@LaunchedEffect
        }
        // A restored navigation stack can exist before the in-memory user is restored.
        if (currentUser?.isProfileCompleted != true) {
            navController.navigate(AuthGraph) {
                popUpTo<MainGraph> { inclusive = true }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        navController.navigate(WidgetMoment(photoId)) {
            popUpTo<MainTabs>()
            launchSingleTop = true
        }
        onWidgetPhotoOpened()
    }
    // Main and Profile own Back for tab selection and sign-out respectively.
    BackHandler(
        enabled = backStackEntry?.destination?.hierarchy?.any { it.hasRoute<AuthGraph>() } == true,
        onBack = onBack,
    )
}
