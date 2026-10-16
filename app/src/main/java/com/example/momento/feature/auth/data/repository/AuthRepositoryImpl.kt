package com.example.momento.feature.auth.data.repository

import android.util.Log
import com.example.momento.core.background.BackgroundWorkScheduler
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.data.mapper.toDomain
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.data.mapper.toDomain
import com.example.momento.feature.auth.data.remote.AuthApi
import com.example.momento.feature.auth.data.remote.dto.CompleteProfileRequest
import com.example.momento.feature.auth.data.remote.dto.LoginRequest
import com.example.momento.feature.auth.domain.repository.AuthProvider
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.example.momento.feature.widget.data.local.WidgetLocalStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Lazy
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Firebase persists the session. The backend user is kept in memory. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebase: Lazy<FirebaseAuth>,
    private val api: AuthApi,
    private val userStore: CurrentUserStore,
    private val widgetStore: WidgetLocalStore,
    private val workScheduler: BackgroundWorkScheduler,
    private val messaging: Lazy<FirebaseMessaging>,
) : AuthRepository {
    override val currentUser = userStore.currentUser
    private val backgroundMutex = Mutex()

    override suspend fun syncUser(provider: AuthProvider): Result<CurrentUser> = suspendRunCatching {
        val session = userStore.beginSession()
        val firebaseUid = checkNotNull(firebase.get().currentUser).uid
        val body = LoginRequest(idToken())
        val response = when (provider) {
            AuthProvider.GOOGLE -> api.google(body)
            AuthProvider.PHONE -> api.phone(body)
        }
        publish(session, response.requireData().toDomain(), firebaseUid)
    }

    override suspend fun restoreSession(): Result<CurrentUser?> = suspendRunCatching {
        val session = userStore.beginSession()
        val firebaseUid = firebase.get().currentUser?.uid
        if (firebaseUid == null) {
            backgroundMutex.withLock {
                widgetStore.clearSession { userStore.checkSession(session) }
                workScheduler.cancelSessionWork()
                messaging.get().isAutoInitEnabled = false
            }
            null
        } else {
            publish(session, api.me().requireData().toDomain(), firebaseUid)
        }
    }

    override suspend fun completeProfile(displayName: String, username: String): Result<CurrentUser> = suspendRunCatching {
        val session = userStore.requireSession()
        val firebaseUid = checkNotNull(firebase.get().currentUser).uid
        val name = displayName.trim()
        val handle = username.trim().lowercase(Locale.ROOT)
        require(name.isNotEmpty()) { "Display name is required" }
        require(name.length <= 50) { "Display name must not exceed 50 characters" }
        require(handle.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            "Username must contain 3-20 lowercase letters, digits or underscores"
        }
        val user = api.completeProfile(CompleteProfileRequest(name, handle)).requireData().toDomain()
        check(user.isProfileCompleted) { "Profile is not complete" }
        publish(session, user, firebaseUid)
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = suspendRunCatching {
        api.checkUsernameAvailability(username).requireData().available
    }

    override suspend fun signOut(): Result<Unit> = suspendRunCatching {
        userStore.clear()
        try {
            backgroundMutex.withLock {
                widgetStore.clearSession()
                workScheduler.cancelSessionWork()
                messaging.get().isAutoInitEnabled = false
                deleteFcmToken()
                if (firebase.get().currentUser != null) api.logout()
            }
        } finally {
            firebase.get().signOut()
        }
    }

    private suspend fun publish(
        session: CurrentUserStore.Session,
        user: CurrentUser,
        firebaseUid: String,
    ): CurrentUser {
        check(firebase.get().currentUser?.uid == firebaseUid) { "Firebase account has changed" }
        userStore.update(session, user)
        try {
            startBackgroundSync(session, user, firebaseUid)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Background setup must not prevent an otherwise successful login.
            Log.w("MomentoBackground", "Unable to initialize background synchronization", error)
        }
        userStore.checkSession(session)
        return user
    }

    private suspend fun startBackgroundSync(
        session: CurrentUserStore.Session,
        user: CurrentUser,
        firebaseUid: String,
    ) {
        backgroundMutex.withLock {
            val checkSession = {
                userStore.checkSession(session)
                check(firebase.get().currentUser?.uid == firebaseUid) { "Firebase account has changed" }
            }

            if (!user.isProfileCompleted) {
                widgetStore.clearSession(checkSession = checkSession)
                workScheduler.cancelSessionWork()
                messaging.get().isAutoInitEnabled = false
                return@withLock
            }

            widgetStore.activate(firebaseUid, user.id, checkSession)
            checkSession()
            messaging.get().isAutoInitEnabled = true
            workScheduler.registerFcmToken()
            workScheduler.refreshWidgetData()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun deleteFcmToken() {
        withTimeoutOrNull(3_000) {
            try {
                messaging.get().deleteToken().await()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("MomentoBackground", "Unable to delete the old FCM token", error)
            }
        }
    }

    private suspend fun idToken(): String {
        val user = checkNotNull(firebase.get().currentUser) { "Sign in first" }
        val token = user.getIdToken(false).await().token
        check(!token.isNullOrBlank()) { "Firebase ID token is missing" }
        return token
    }
}
