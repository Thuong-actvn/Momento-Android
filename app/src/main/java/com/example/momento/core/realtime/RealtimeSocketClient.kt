package com.example.momento.core.realtime

import com.example.momento.core.network.AuthInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.OkHttpClient
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.stomp.use
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient
import retrofit2.Retrofit

interface RealtimeSocketClient {
    suspend fun listen(onSubscribed: () -> Unit, onMessage: suspend (String) -> Unit)
}

@Singleton
class StompRealtimeSocketClient @Inject constructor(
    retrofit: Retrofit,
    authInterceptor: AuthInterceptor,
) : RealtimeSocketClient {
    // Spring's /ws SockJS endpoint also exposes raw STOMP at /ws/websocket.
    private val endpoint = checkNotNull(retrofit.baseUrl().resolve("/ws/websocket"))

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

    override suspend fun listen(onSubscribed: () -> Unit, onMessage: suspend (String) -> Unit) {
        val session = stompClient.connect(endpoint.toString())
        session.use {
            val messages = session.subscribeText("/user/queue/events")
            onSubscribed()

            messages.collect { body ->
                onMessage(body)
            }
        }
    }
}
