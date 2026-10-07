package com.example.momento.feature.auth.data.mapper

import com.example.momento.feature.auth.data.remote.dto.LoginResponse
import com.example.momento.feature.auth.data.remote.dto.ProfileDto
import com.example.momento.feature.auth.data.remote.dto.UserDto
import com.example.momento.feature.auth.domain.model.AuthUser

fun UserDto.toDomain(completed: Boolean) = AuthUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    email = email,
    phoneNumber = phoneNumber,
    isProfileCompleted = completed,
)

// Existing accounts may also have an unfinished profile.
fun LoginResponse.toDomain(): AuthUser = user.toDomain(completed = !isProfileIncomplete)

fun ProfileDto.toDomain(): AuthUser = AuthUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    email = email,
    phoneNumber = phoneNumber,
    isProfileCompleted = isProfileCompleted,
)
