package com.example.momento.feature.chat.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.feature.chat.data.remote.dto.ConversationSummaryDto
import com.example.momento.feature.chat.data.remote.dto.MessagePageDto
import com.example.momento.feature.chat.data.remote.dto.MessageResponseDto
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ChatApi {
    @GET("conversations")
    suspend fun getConversations(): ApiResponse<List<ConversationSummaryDto>>

    @GET("conversations/{userId}/messages")
    suspend fun getMessages(
        @Path("userId") otherUserId: String,
        @Query("cursor") cursor: String?,
        @Query("limit") limit: Int,
    ): ApiResponse<MessagePageDto>

    @POST("conversations/{userId}/messages")
    suspend fun sendText(
        @Path("userId") otherUserId: String,
        @Body body: RequestBody,
    ): ApiResponse<MessageResponseDto>

    @POST("photos/{photoId}/comments")
    suspend fun replyToMoment(
        @Path("photoId") photoId: String,
        @Body body: RequestBody,
    ): ApiResponse<MessageResponseDto>
}
