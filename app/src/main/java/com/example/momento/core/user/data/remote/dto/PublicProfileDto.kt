package com.example.momento.core.user.data.remote.dto

import com.example.momento.core.user.domain.model.PublicProfile
import java.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PublicProfileDto(
    val id: String,
    val username: String,
    val createdAt: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

fun PublicProfileDto.toDomain(): PublicProfile = PublicProfile(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    createdAt = Instant.parse(createdAt),
)
