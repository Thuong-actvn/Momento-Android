package com.example.momento.core.user.data

import com.example.momento.core.user.domain.model.CurrentUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy

/** In-memory backend user shared by auth, profile and the main screen. */
@Singleton
class CurrentUserStore @Inject constructor() {
    private val _currentUser = MutableStateFlow<CurrentUser?>(null)
    val currentUser = _currentUser.asStateFlow()
    private var generation = 0L
    private val _sessionVersion = MutableStateFlow(generation)

    /** Changes even when the same account signs out and immediately signs in again. */
    val sessionVersion = _sessionVersion.asStateFlow()

    private val _completedProfileSession = MutableStateFlow<Session?>(null)
    val completedProfileSession = _completedProfileSession.asStateFlow()
    private var usingCachedSession = false

    class Session internal constructor(
        internal val generation: Long,
        internal val userId: String?,
    )

    @Synchronized
    fun beginSession(): Session {
        clear()
        return Session(generation, null)
    }

    @Synchronized
    fun requireSession(): Session {
        return checkNotNull(sessionOrNull()) { "Sign in first" }
    }

    @Synchronized
    fun sessionOrNull(): Session? {
        val user = _currentUser.value ?: return null
        return Session(generation, user.id)
    }

    fun observeSession(): Flow<Session?> {
        val sessions = combine(currentUser, sessionVersion) { _, _ -> sessionOrNull() }
        return sessions.distinctUntilChangedBy { session -> session?.generation }
    }

    @Synchronized
    fun checkSession(session: Session) {
        check(session.generation == generation) { "User session has changed" }
    }

    /** Publish session-owned state atomically with respect to sign-out. Never suspend here. */
    @Synchronized
    fun <T> withSession(session: Session, block: () -> T): T {
        checkSession(session)
        return block()
    }

    @Synchronized
    fun update(session: Session, user: CurrentUser) {
        publish(session, user, fromCache = usingCachedSession)
    }

    @Synchronized
    fun restoreFromCache(session: Session, user: CurrentUser) {
        publish(session, user, fromCache = true)
    }

    @Synchronized
    fun confirmSession(session: Session, user: CurrentUser) {
        publish(session, user, fromCache = false)
    }

    @Synchronized
    fun isUsingCachedSession(): Boolean {
        return usingCachedSession
    }

    private fun publish(session: Session, user: CurrentUser, fromCache: Boolean) {
        checkSession(session)
        check(session.userId == null || session.userId == user.id) {
            "Backend returned a different user"
        }
        usingCachedSession = fromCache
        _currentUser.value = user
        if (!user.isProfileCompleted || fromCache) {
            _completedProfileSession.value = null
        } else if (_completedProfileSession.value?.userId != user.id) {
            _completedProfileSession.value = Session(generation, user.id)
        }
    }

    @Synchronized
    fun clear(): String? {
        val accountId = _currentUser.value?.id
        generation++
        usingCachedSession = false
        _currentUser.value = null
        _sessionVersion.value = generation
        _completedProfileSession.value = null
        return accountId
    }
}
