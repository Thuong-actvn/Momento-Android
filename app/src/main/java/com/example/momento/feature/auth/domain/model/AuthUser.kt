package com.example.momento.feature.auth.domain.model

/** id is the backend UUID. Firebase owns its UID and credentials separately. */
data class AuthUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val email: String?,
    val phoneNumber: String?,
    val isProfileCompleted: Boolean,
)

enum class AuthProvider { GOOGLE, PHONE }
