package com.example.momento.core.user.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val username: String,
    val isProfileCompleted: Boolean,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val email: String? = null,
    val phoneNumber: String? = null,
    val birthday: String? = null,
    val createdAt: String? = null,
)
