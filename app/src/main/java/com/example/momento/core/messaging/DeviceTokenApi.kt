package com.example.momento.core.messaging

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.PATCH

interface DeviceTokenApi {
    @PATCH("users/me/fcm-token")
    suspend fun updateToken(@Body body: FcmTokenRequest)
}

@Serializable
data class FcmTokenRequest(val fcmToken: String)
