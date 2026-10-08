package com.example.momento.feature.auth.data.mapper

import com.example.momento.feature.auth.data.remote.dto.LoginResponse
import com.example.momento.feature.auth.data.remote.dto.UserDto
import com.example.momento.core.user.domain.model.CurrentUser
import java.time.LocalDate

fun UserDto.toDomain(completed: Boolean) = CurrentUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    email = email,
    phoneNumber = phoneNumber,
    isProfileCompleted = completed,
    birthday = birthday?.let(LocalDate::parse),
)

// Existing accounts may also have an unfinished profile.
fun LoginResponse.toDomain(): CurrentUser = user.toDomain(completed = !isProfileIncomplete)
