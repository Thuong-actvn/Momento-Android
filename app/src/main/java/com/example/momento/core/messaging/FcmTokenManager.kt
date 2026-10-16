package com.example.momento.core.messaging

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.widget.data.WidgetSessionManager

class FcmTokenManager(
    private val deviceTokenApi: DeviceTokenApi,
    private val sessionManager: WidgetSessionManager,
    private val getToken: suspend () -> String,
) {
    suspend fun register(): Result<Unit> = suspendRunCatching {
        if (sessionManager.current() == null) {
            return@suspendRunCatching
        }

        sessionManager.withAccount { account ->
            val token = getToken()
            check(token.isNotBlank()) { "FCM registration token is missing" }
            sessionManager.checkCurrent(account)
            deviceTokenApi.updateToken(FcmTokenRequest(fcmToken = token))
        }
    }
}
