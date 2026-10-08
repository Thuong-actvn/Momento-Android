package com.example.momento.core.user.domain.model

import java.time.Instant
import java.time.LocalDate

data class CurrentUser(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val email: String?,
    val phoneNumber: String?,
    val isProfileCompleted: Boolean,
    val birthday: LocalDate? = null,
    val createdAt: Instant? = null,
)
