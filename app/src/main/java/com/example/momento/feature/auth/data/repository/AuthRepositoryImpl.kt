package com.example.momento.feature.auth.data.repository

import android.util.Log
import com.example.momento.core.background.BackgroundWorkScheduler
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.data.mapper.toDomain
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.data.local.OfflineSessionStore
import com.example.momento.feature.auth.data.mapper.toDomain
import com.example.momento.feature.auth.data.remote.AuthApi
import com.example.momento.feature.auth.data.remote.dto.CompleteProfileRequest
import com.example.momento.feature.auth.data.remote.dto.LoginRequest
import com.example.momento.feature.auth.domain.repository.AuthProvider
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.example.momento.feature.auth.domain.repository.RestoredSession
import com.example.momento.feature.widget.data.local.WidgetLocalStore
import com.example.momento.feature.widget.presentation.home.WidgetRenderer
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Lazy
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Keeps the cached account active while online verification is unavailable. */
@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val firebase: Lazy<FirebaseAuth>,
    private val api: AuthApi,
    private val userStore: CurrentUserStore,
    private val offlineSessions: OfflineSessionStore,
    private val widgetStore: WidgetLocalStore,
    private val workScheduler: BackgroundWorkScheduler,
    private val messaging: Lazy<FirebaseMessaging>,
    private val widgetRenderer: WidgetRenderer,
) : AuthRepository {
    override val currentUser = userStore.currentUser
    private val authMutex = Mutex()

    override suspend fun syncUser(provider: AuthProvider): Result<CurrentUser> = suspendRunCatching {
        authMutex.withLock {
            val session = userStore.beginSession()
            val firebaseUid = checkNotNull(firebase.get().currentUser).uid
            val savedAccount = offlineSessions.read()
            if (savedAccount != null && savedAccount.firebaseUid != firebaseUid) {
                offlineSessions.forget()
            }
            val body = LoginRequest(idToken())
            val response = when (provider) {
                AuthProvider.GOOGLE -> api.google(body)
                AuthProvider.PHONE -> api.phone(body)
            }
            publish(session, response.requireData().toDomain(), firebaseUid)
        }
    }

    override suspend fun restoreSession(): Result<RestoredSession> = suspendRunCatching {
        authMutex.withLock {
            restore()
        }
    }

    override suspend fun revalidateSession(firebaseUid: String): Result<RestoredSession> = suspendRunCatching {
        authMutex.withLock {
            val session = userStore.sessionOrNull() ?: return@withLock RestoredSession.SignedOut
            val cachedUser = checkNotNull(currentUser.value)
            if (!userStore.isUsingCachedSession()) {
                return@withLock RestoredSession.Active(cachedUser)
            }
            if (firebase.get().currentUser?.uid != firebaseUid) {
                revokeCachedSession()
                return@withLock RestoredSession.SignedOut
            }

            val user = try {
                api.me().requireData().toDomain()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (firebase.get().currentUser?.uid != firebaseUid ||
                    classifySessionRestoreFailure(error) == SessionRestoreFailure.Denied
                ) {
                    revokeCachedSession()
                    throw error
                }
                return@withLock RestoredSession.Active(cachedUser, firebaseUid)
            }

            if (firebase.get().currentUser?.uid != firebaseUid || user.id != cachedUser.id) {
                revokeCachedSession()
                return@withLock RestoredSession.SignedOut
            }
            RestoredSession.Active(publish(session, user, firebaseUid))
        }
    }

    private suspend fun revokeCachedSession() {
        val accountId = userStore.clear()
        offlineSessions.forget(accountId)
        clearBackgroundSession()
    }

    private suspend fun restore(): RestoredSession {
        val session = userStore.beginSession()
        val firebaseUid = firebase.get().currentUser?.uid
        val savedAccount = offlineSessions.read()
        if (savedAccount != null && savedAccount.firebaseUid != firebaseUid) {
            offlineSessions.forget()
        }

        if (firebaseUid == null) {
            clearBackgroundSession { userStore.checkSession(session) }
            return RestoredSession.SignedOut
        }

        val user = try {
            api.me().requireData().toDomain()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (firebase.get().currentUser?.uid != firebaseUid) {
                offlineSessions.forget()
                clearBackgroundSession { userStore.checkSession(session) }
                throw error
            }
            when (classifySessionRestoreFailure(error)) {
                SessionRestoreFailure.Network -> {
                    if (savedAccount?.firebaseUid == firebaseUid) {
                        workScheduler.cancelSessionWork()
                        messaging.get().isAutoInitEnabled = false
                        val user = CurrentUser(
                            id = savedAccount.backendUserId,
                            username = "",
                            displayName = savedAccount.displayName,
                            avatarUrl = null,
                            email = null,
                            phoneNumber = null,
                            isProfileCompleted = true,
                        )
                        userStore.restoreFromCache(session, user)
                        return RestoredSession.Active(user, firebaseUid)
                    }
                }
                SessionRestoreFailure.Denied -> {
                    offlineSessions.forget()
                    clearBackgroundSession { userStore.checkSession(session) }
                }
                SessionRestoreFailure.Other -> Unit
            }
            throw error
        }

        if (firebase.get().currentUser?.uid != firebaseUid) {
            offlineSessions.forget()
            clearBackgroundSession { userStore.checkSession(session) }
            error("Firebase account has changed")
        }
        return RestoredSession.Active(publish(session, user, firebaseUid))
    }

    override suspend fun completeProfile(displayName: String, username: String): Result<CurrentUser> = suspendRunCatching {
        val name = displayName.trim()
        val handle = username.trim().lowercase(Locale.ROOT)
        require(name.isNotEmpty()) { "Display name is required" }
        require(name.length <= 50) { "Display name must not exceed 50 characters" }
        require(handle.matches(Regex("^[a-z0-9_]{3,20}$"))) {
            "Username must contain 3-20 lowercase letters, digits or underscores"
        }
        authMutex.withLock {
            val session = userStore.requireSession()
            val firebaseUid = checkNotNull(firebase.get().currentUser).uid
            val user = api.completeProfile(CompleteProfileRequest(name, handle)).requireData().toDomain()
            check(user.isProfileCompleted) { "Profile is not complete" }
            publish(session, user, firebaseUid)
        }
    }

    override suspend fun checkUsernameAvailability(username: String): Result<Boolean> = suspendRunCatching {
        api.checkUsernameAvailability(username).requireData().available
    }

    override suspend fun signOut(): Result<Unit> = suspendRunCatching {
        authMutex.withLock {
            try {
                val wasOffline = userStore.isUsingCachedSession()
                val cleared = withContext(NonCancellable) {
                    suspendRunCatching {
                        val activeUserId = userStore.clear()
                        offlineSessions.forget(activeUserId)
                    }
                }
                clearBackgroundSession()
                if (!wasOffline) {
                    deleteFcmToken()
                    if (firebase.get().currentUser != null) api.logout()
                }
                cleared.getOrThrow()
            } finally {
                firebase.get().signOut()
            }
        }
    }

    private suspend fun publish(
        session: CurrentUserStore.Session,
        user: CurrentUser,
        firebaseUid: String,
    ): CurrentUser {
        check(firebase.get().currentUser?.uid == firebaseUid) { "Firebase account has changed" }
        userStore.checkSession(session)
        if (user.isProfileCompleted) {
            offlineSessions.save(firebaseUid, user)
        } else {
            offlineSessions.forget()
        }
        if (firebase.get().currentUser?.uid != firebaseUid) {
            offlineSessions.forget()
            error("Firebase account has changed")
        }
        userStore.confirmSession(session, user)
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
        val checkSession = {
            userStore.checkSession(session)
            check(firebase.get().currentUser?.uid == firebaseUid) { "Firebase account has changed" }
        }

        if (!user.isProfileCompleted) {
            clearBackgroundSession(checkSession)
            return
        }

        widgetStore.activate(firebaseUid, user.id, checkSession)
        checkSession()
        workScheduler.updateWidgets()
        messaging.get().isAutoInitEnabled = true
        workScheduler.registerFcmToken()
        workScheduler.refreshWidgetData()
    }

    private suspend fun clearBackgroundSession(checkSession: () -> Unit = {}) {
        widgetStore.clearSession(checkSession = checkSession)
        widgetRenderer.clear()
        workScheduler.cancelSessionWork()
        messaging.get().isAutoInitEnabled = false
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
