package com.example.momento.core.user.domain.model

import java.time.Instant

/** Public backend profile used by search, friendships and incoming requests. */
data class PublicProfile(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val createdAt: Instant,
)
