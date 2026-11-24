package com.example.momento.feature.moment.data.realtime

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Screens reload photos through REST; widgets reuse their existing background work. */
class MomentUpdates(
    private val users: CurrentUserStore,
    scope: CoroutineScope,
    refreshWidgets: () -> Unit,
) {
    private val _version = MutableStateFlow(0L)
    val version = _version.asStateFlow()

    // Reaction changes only reload an open reaction list, without refreshing photos or widgets.
    private val _reactionVersion = MutableStateFlow(0L)
    val reactionVersion = _reactionVersion.asStateFlow()

    init {
        scope.launch {
            version.collectLatest { version ->
                if (version == 0L) return@collectLatest
                val session = users.completedProfileSession.value ?: return@collectLatest
                delay(500)
                suspendRunCatching {
                    users.withSession(session) {
                        if (users.completedProfileSession.value == session) {
                            refreshWidgets()
                        }
                    }
                }
            }
        }
    }

    fun requestRefresh(session: CurrentUserStore.Session) {
        users.withSession(session) {
            _version.value = _version.value + 1
        }
    }

    fun requestReactionRefresh(session: CurrentUserStore.Session) {
        users.withSession(session) {
            _reactionVersion.value = _reactionVersion.value + 1
        }
    }
}
