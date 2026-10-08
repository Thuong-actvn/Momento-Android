package com.example.momento.core.user.data.mapper

import com.example.momento.core.user.data.remote.dto.ProfileDto
import com.example.momento.core.user.domain.model.CurrentUser
import java.time.Instant
import java.time.LocalDate

fun ProfileDto.toDomain(): CurrentUser = CurrentUser(
    id = id,
    username = username,
    displayName = displayName,
    avatarUrl = avatarUrl,
    email = email,
    phoneNumber = phoneNumber,
    isProfileCompleted = isProfileCompleted,
    birthday = birthday?.let(LocalDate::parse),
    createdAt = createdAt?.let(Instant::parse),
)
