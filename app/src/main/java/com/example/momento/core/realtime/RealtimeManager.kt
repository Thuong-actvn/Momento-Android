package com.example.momento.core.realtime

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.chat.data.realtime.ChatRealtime
import com.example.momento.feature.moment.data.realtime.MomentUpdates
import com.example.momento.feature.notification.data.realtime.NotificationUpdates
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

enum class RealtimeConnectionState {
    STOPPED,
    CONNECTING,
    CONNECTED,
    WAITING_TO_RETRY,
}

@Singleton
class RealtimeManager @Inject constructor(
    private val socket: RealtimeSocketClient,
    private val network: RealtimeNetworkMonitor,
    private val chat: ChatRealtime,
    private val notifications: NotificationUpdates,
    private val moments: MomentUpdates,
    private val users: CurrentUserStore,
    scope: CoroutineScope,
) {
    private val foreground = MutableStateFlow(false)
    private val state = MutableStateFlow(RealtimeConnectionState.STOPPED)
    val connectionState = state.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
    }

    init {
        scope.launch {
            foreground.collectLatest { visible ->
                if (visible) {
                    users.completedProfileSession.collectLatest { session ->
                        if (session != null) {
                            runSession(session)
                        }
                    }
                }
            }
        }
    }

    fun setForeground(visible: Boolean) {
        foreground.value = visible
    }

    private suspend fun runSession(session: CurrentUserStore.Session) = coroutineScope {
        val syncRequested = MutableStateFlow(true)
        val networkRestored = MutableStateFlow(false)
        val networkWatch = network.watch {
            networkRestored.value = true
        }

        try {
            notifications.requestRefresh(session)
            moments.requestRefresh(session)
            moments.requestReactionRefresh(session)
            launch {
                syncChat(session, syncRequested)
            }
            listenWithRetry(session, syncRequested, networkRestored)
        } finally {
            chat.endForeground(session)
            networkWatch.close()
            state.value = RealtimeConnectionState.STOPPED
        }
    }

    private suspend fun listenWithRetry(
        session: CurrentUserStore.Session,
        syncRequested: MutableStateFlow<Boolean>,
        networkRestored: MutableStateFlow<Boolean>,
    ) {
        while (true) {
            networkRestored.value = false
            state.value = RealtimeConnectionState.CONNECTING
            suspendRunCatching {
                socket.listen(
                    onSubscribed = {
                        users.checkSession(session)
                        state.value = RealtimeConnectionState.CONNECTED
                        syncRequested.value = true
                        notifications.requestRefresh(session)
                        moments.requestRefresh(session)
                        moments.requestReactionRefresh(session)
                    },
                    onMessage = { body ->
                        if (handleMessage(session, body)) {
                            syncRequested.value = true
                        }
                    },
                )
            }

            state.value = RealtimeConnectionState.WAITING_TO_RETRY
            withTimeoutOrNull(RECONNECT_DELAY_MS) {
                networkRestored.first { available -> available }
            }
        }
    }

    private suspend fun handleMessage(session: CurrentUserStore.Session, body: String): Boolean {
        try {
            val event = json.decodeFromString<Envelope>(body)
            if (event.type == "CHAT_MESSAGE" && event.payload != null) {
                return chat.receiveMessage(session, event.payload)
            }
            if (event.type == "NOTIFICATION") {
                notifications.requestRefresh(session)
            }
            if (event.type == "WIDGET_UPDATE") {
                moments.requestRefresh(session)
            }
            if (event.type == "REACTION") {
                moments.requestReactionRefresh(session)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A malformed message can be skipped without closing the socket.
        }
        return false
    }

    private suspend fun syncChat(
        session: CurrentUserStore.Session,
        syncRequested: MutableStateFlow<Boolean>,
    ) {
        while (true) {
            withTimeoutOrNull(SYNC_INTERVAL_MS) {
                syncRequested.first { requested -> requested }
            }
            // Several messages can share one REST request. A message during the request schedules the next one.
            delay(500)
            syncRequested.value = false
            suspendRunCatching {
                chat.synchronize(session)
            }
        }
    }

    @Serializable
    private data class Envelope(val type: String, val payload: JsonElement? = null)

    private companion object {
        const val RECONNECT_DELAY_MS = 3_000L
        const val SYNC_INTERVAL_MS = 30_000L
    }
}
