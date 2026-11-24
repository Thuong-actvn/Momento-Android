package com.example.momento.feature.notification.data.realtime

import com.example.momento.core.user.data.CurrentUserStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** NOTIFICATION has no payload. Screens reload their data through REST. */
@Singleton
class NotificationUpdates @Inject constructor(
    private val users: CurrentUserStore,
) {
    private val _version = MutableStateFlow(0L)
    val version = _version.asStateFlow()

    fun requestRefresh(session: CurrentUserStore.Session) {
        users.withSession(session) {
            _version.value = _version.value + 1
        }
    }
}
