package com.example.momento.feature.profile.domain.repository

import com.example.momento.core.user.domain.model.CurrentUser
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    val currentUser: StateFlow<CurrentUser?>

    suspend fun refreshProfile(): Result<CurrentUser>

    /** Null omits a field. The backend cannot clear a value using JSON null. */
    suspend fun updateProfile(
        displayName: String? = null,
        birthday: LocalDate? = null,
    ): Result<CurrentUser>

    /** Clears the local session even when the backend logout fails. */
    suspend fun signOut(): Result<Unit>
}
