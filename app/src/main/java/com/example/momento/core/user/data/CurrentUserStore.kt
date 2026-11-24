package com.example.momento.core.user.data

import com.example.momento.core.user.domain.model.CurrentUser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
        val user = checkNotNull(_currentUser.value) { "Sign in first" }
        return Session(generation, user.id)
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
        checkSession(session)
        check(session.userId == null || session.userId == user.id) {
            "Backend returned a different user"
        }
        _currentUser.value = user
        if (!user.isProfileCompleted) {
            _completedProfileSession.value = null
        } else if (_completedProfileSession.value?.userId != user.id) {
            _completedProfileSession.value = Session(generation, user.id)
        }
    }

    @Synchronized
    fun clear() {
        generation++
        _sessionVersion.value = generation
        _currentUser.value = null
        _completedProfileSession.value = null
    }
}
