package com.example.momento.feature.auth.domain.repository

import com.example.momento.core.user.domain.model.CurrentUser
import kotlinx.coroutines.flow.StateFlow

enum class AuthProvider { GOOGLE, PHONE }

interface AuthRepository {
    val currentUser: StateFlow<CurrentUser?>

    /** Called by the Google/Phone adapter after Firebase sign-in succeeds. */
    suspend fun syncUser(provider: AuthProvider): Result<CurrentUser>
    suspend fun restoreSession(): Result<CurrentUser?>
    suspend fun checkUsernameAvailability(username: String): Result<Boolean>
    suspend fun completeProfile(displayName: String, username: String): Result<CurrentUser>
    suspend fun signOut(): Result<Unit>
}
