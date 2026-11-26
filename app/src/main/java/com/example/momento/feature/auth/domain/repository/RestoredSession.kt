package com.example.momento.feature.auth.domain.repository

import com.example.momento.core.user.domain.model.CurrentUser

sealed interface RestoredSession {
    data object SignedOut : RestoredSession
    data class Active(val user: CurrentUser, val cachedFirebaseUid: String? = null) : RestoredSession
}

data class SavedAccount(
    val firebaseUid: String,
    val backendUserId: String,
    val displayName: String,
)
