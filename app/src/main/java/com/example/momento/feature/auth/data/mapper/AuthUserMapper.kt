package com.example.momento.feature.auth.data.mapper

import com.example.momento.feature.auth.data.remote.dto.LoginResponse
import com.example.momento.feature.auth.data.remote.dto.UserDto
import com.example.momento.feature.auth.domain.model.AuthUser

internal fun UserDto.toDomain(completed: Boolean = isProfileCompleted) = AuthUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    email = email,
    phoneNumber = phoneNumber,
    isProfileCompleted = completed,
)

// Existing accounts may also have an unfinished profile.
internal fun LoginResponse.toDomain(): AuthUser = user.toDomain(completed = !isProfileIncomplete)
