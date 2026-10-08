package com.example.momento.feature.auth.domain.repository

import com.example.momento.feature.auth.domain.model.AuthProvider
import com.example.momento.feature.auth.domain.model.AuthUser
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val currentUser: StateFlow<AuthUser?>

    /** Called by the Google/Phone adapter after Firebase sign-in succeeds. */
    suspend fun syncUser(provider: AuthProvider): Result<AuthUser>
    suspend fun restoreSession(): Result<AuthUser?>
    suspend fun checkUsernameAvailability(username: String): Result<Boolean>
    suspend fun completeProfile(displayName: String, username: String): Result<AuthUser>
    suspend fun signOut(): Result<Unit>
}
