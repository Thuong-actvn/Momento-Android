package com.example.momento.feature.auth.data.repository

import com.example.momento.feature.auth.data.suspendRunCatching
import com.example.momento.feature.auth.data.mapper.toDomain
import com.example.momento.feature.auth.data.remote.AuthApi
import com.example.momento.feature.auth.data.remote.dto.DisplayNameRequest
import com.example.momento.feature.auth.data.remote.dto.LoginRequest
import com.example.momento.feature.auth.data.remote.dto.UsernameRequest
import com.example.momento.feature.auth.domain.model.AuthProvider
import com.example.momento.feature.auth.domain.model.AuthUser
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.Lazy
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Firebase persists the session. The backend user is kept in memory. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebase: Lazy<FirebaseAuth>,
    private val api: AuthApi,
) : AuthRepository {
    private val _currentUser = MutableStateFlow<AuthUser?>(null)
    override val currentUser = _currentUser.asStateFlow()

    override suspend fun syncUser(provider: AuthProvider): Result<AuthUser> = suspendRunCatching {
        _currentUser.value = null
        val body = LoginRequest(idToken())
        val response = when (provider) {
            AuthProvider.GOOGLE -> api.google(body)
            AuthProvider.PHONE -> api.phone(body)
        }
        response.requireData().toDomain().also { _currentUser.value = it }
    }

    override suspend fun restoreSession(): Result<AuthUser?> = suspendRunCatching {
        if (firebase.get().currentUser == null) {
            _currentUser.value = null
            null
        } else {
            api.me().requireData()
                .toDomain().also { _currentUser.value = it }
        }
    }

    override suspend fun completeProfile(displayName: String, username: String): Result<AuthUser> = suspendRunCatching {
        val name = displayName.trim()
        val handle = username.trim().lowercase(Locale.ROOT)
        require(name.isNotEmpty()) { "Display name is required" }
        require(handle.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            "Username must contain 3-20 lowercase letters, digits or underscores"
        }
        // The backend marks the profile complete when the username is saved.
        api.displayName(DisplayNameRequest(name)).requireData()
        api.username(UsernameRequest(handle)).requireData()
            .toDomain().also { _currentUser.value = it }
    }

    override suspend fun signOut(): Result<Unit> = suspendRunCatching {
        try {
            if (firebase.get().currentUser != null) api.logout()
        } finally {
            _currentUser.value = null
            firebase.get().signOut()
        }
    }

    private suspend fun idToken(): String {
        val user = checkNotNull(firebase.get().currentUser) { "Sign in first" }
        val token = user.getIdToken(false).await().token
        check(!token.isNullOrBlank()) { "Firebase ID token is missing" }
        return token
    }
}
