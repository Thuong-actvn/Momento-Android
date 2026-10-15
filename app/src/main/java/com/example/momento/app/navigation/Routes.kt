package com.example.momento.app.navigation

import kotlinx.serialization.Serializable

@Serializable data object MainGraph

@Serializable data object MainTabs

@Serializable data object Profile

@Serializable data object EditProfile

@Serializable data object Friends

@Serializable
data object ChatList

@Serializable
data class ChatDetail(
    val otherUserId: String,
)
