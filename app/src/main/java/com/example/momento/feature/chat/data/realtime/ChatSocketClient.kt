package com.example.momento.feature.chat.data.realtime

import com.example.momento.core.network.AuthInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.stomp.use
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient
import retrofit2.Retrofit

sealed interface ChatSocketEvent {
    data object Connected : ChatSocketEvent
    data class Message(val body: String) : ChatSocketEvent
}

@Singleton
class ChatSocketClient @Inject constructor(
    retrofit: Retrofit,
    authInterceptor: AuthInterceptor,
) {
    // Spring's /ws SockJS endpoint also exposes raw STOMP at /ws/websocket.
    private val socketUrl = retrofit.baseUrl()
        .newBuilder()
        .encodedPath("/ws/websocket")
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val stompClient = StompClient(OkHttpWebSocketClient(httpClient)) {
        // Sending uses REST, so closing the socket does not need a STOMP receipt.
        gracefulDisconnect = false
    }

    fun events(): Flow<ChatSocketEvent> = flow {
        val session = stompClient.connect(socketUrl)
        session.use { connectedSession ->
            val messages = connectedSession.subscribeText("/user/queue/events")
            emit(ChatSocketEvent.Connected)

            messages.collect { body ->
                emit(ChatSocketEvent.Message(body))
            }
        }
    }
}
