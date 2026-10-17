package com.example.momento.feature.notification.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.feature.notification.data.remote.dto.NotificationPageDto
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {
    @GET("notifications")
    suspend fun getNotifications(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<NotificationPageDto>

    @PATCH("notifications/{id}/read")
    suspend fun markAsRead(@Path("id") id: String): ApiResponse<Unit>

    @PATCH("notifications/read-all")
    suspend fun markAllAsRead(): ApiResponse<Unit>
}
