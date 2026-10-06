package com.example.momento.feature.auth.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val isProfileCompleted: Boolean = false,
)

@Serializable
data class LoginRequest(val firebaseIdToken: String) {
    override fun toString(): String = "LoginRequest(<redacted>)"
}

@Serializable
data class LoginResponse(
    val user: UserDto,
    val isNewUser: Boolean,
    val isProfileIncomplete: Boolean,
)

@Serializable data class DisplayNameRequest(val displayName: String)
@Serializable data class UsernameRequest(val username: String)
