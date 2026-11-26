package com.example.momento.app.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.core.realtime.RealtimeNetworkMonitor
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.example.momento.feature.auth.domain.repository.RestoredSession
import com.google.firebase.auth.FirebaseAuth
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SessionStartupState {
    data object Checking : SessionStartupState
    data class Ready(
        val user: CurrentUser? = null,
        val restoreFailed: Boolean = false,
    ) : SessionStartupState
}

@HiltViewModel
class SessionStartupViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val firebase: Lazy<FirebaseAuth>,
    private val network: RealtimeNetworkMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow<SessionStartupState>(SessionStartupState.Checking)
    val state = _state.asStateFlow()
    private var request: Job? = null
    private var networkWatch: AutoCloseable? = null
    private var retryJob: Job? = null
    private var cachedFirebaseUid: String? = null
    private val identityListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = cachedFirebaseUid
        if (uid != null && firebaseAuth.currentUser?.uid != uid) {
            if (auth.currentUser.value == null) {
                stopWatching()
            } else {
                _state.value = SessionStartupState.Checking
                request?.cancel()
                request = null
                revalidate()
            }
        }
    }

    init {
        restore()
    }

    fun restore() {
        if (request?.isActive == true) {
            return
        }
        _state.value = SessionStartupState.Checking
        request = viewModelScope.launch {
            showSession(auth.restoreSession())
        }
    }

    private fun revalidate() {
        val uid = cachedFirebaseUid ?: return
        if (request?.isActive == true) {
            return
        }
        request = viewModelScope.launch {
            showSession(auth.revalidateSession(uid))
        }
    }

    private fun showSession(result: Result<RestoredSession>) {
        val active = result.getOrNull() as? RestoredSession.Active
        val uid = active?.cachedFirebaseUid
        if (uid == null) {
            stopWatching()
        } else {
            cachedFirebaseUid = uid
            watchCachedSession()
        }
        _state.value = SessionStartupState.Ready(user = active?.user, restoreFailed = result.isFailure)
    }

    private fun watchCachedSession() {
        if (networkWatch != null) {
            return
        }
        networkWatch = network.watch {
            viewModelScope.launch { revalidate() }
        }
        firebase.get().addAuthStateListener(identityListener)
        retryJob = viewModelScope.launch {
            // A network can become available before the backend starts responding.
            while (true) {
                delay(10_000)
                if (network.hasNetwork()) {
                    revalidate()
                }
            }
        }
    }

    private fun stopWatching() {
        cachedFirebaseUid = null
        retryJob?.cancel()
        retryJob = null
        networkWatch?.close()
        if (networkWatch != null) {
            firebase.get().removeAuthStateListener(identityListener)
            networkWatch = null
        }
    }

    override fun onCleared() {
        stopWatching()
    }
}
