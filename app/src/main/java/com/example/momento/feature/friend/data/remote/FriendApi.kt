package com.example.momento.feature.friend.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.core.user.data.remote.dto.PublicProfileDto
import com.example.momento.feature.friend.data.remote.dto.FriendRequestDto
import com.example.momento.feature.friend.data.remote.dto.RespondFriendRequest
import com.example.momento.feature.friend.data.remote.dto.SendFriendRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface FriendApi {
    @GET("friends")
    suspend fun getFriends(): ApiResponse<List<PublicProfileDto>>

    @GET("friends/requests/pending")
    suspend fun getPendingRequests(): ApiResponse<List<FriendRequestDto>>

    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
    ): ApiResponse<List<PublicProfileDto>>

    @POST("friends/requests")
    suspend fun sendRequest(@Body body: SendFriendRequest)

    @PATCH("friends/requests/{requestId}")
    suspend fun respondRequest(
        @Path("requestId") requestId: String,
        @Body body: RespondFriendRequest,
    )

    @DELETE("friends/{friendUserId}")
    suspend fun removeFriend(@Path("friendUserId") friendUserId: String)
}
