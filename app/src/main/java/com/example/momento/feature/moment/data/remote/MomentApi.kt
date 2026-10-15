package com.example.momento.feature.moment.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.feature.moment.data.remote.dto.CursorPagedResponsePhotoResponseDto
import com.example.momento.feature.moment.data.remote.dto.PhotoResponseDto
import com.example.momento.feature.moment.data.remote.dto.ReactionResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface MomentApi {
    @GET("photos/{photoId}")
    suspend fun getPhoto(@Path("photoId") photoId: String): PhotoResponseDto

    @GET("feed")
    suspend fun getFeed(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
    ): CursorPagedResponsePhotoResponseDto

    @GET("feed/friends/{friendId}")
    suspend fun getFriendFeed(
        @Path("friendId") friendId: String,
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
    ): CursorPagedResponsePhotoResponseDto

    @GET("photos/me")
    suspend fun getMyPhotos(
        @Query("cursor") cursor: String? = null,
        @Query("limit") limit: Int = DEFAULT_PAGE_SIZE,
    ): CursorPagedResponsePhotoResponseDto

    @Multipart
    @POST("photos")
    // Swagger defines text fields as query parameters; only image is a multipart part.
    // Pass null audienceUserIds for ALL_FRIENDS. CUSTOM may have an empty list.
    // Retrofit repeats the query key for lists. metadata, if used, is a JSON string.
    suspend fun uploadPhoto(
        @Part image: MultipartBody.Part,
        @Query("caption") caption: String? = null,
        @Query("audienceType") audienceType: String,
        @Query("audienceUserIds") audienceUserIds: List<String>? = null,
        @Query("metadata") metadata: String? = null,
    ): PhotoResponseDto

    @POST("photos/{photoId}/reactions")
    suspend fun reactToPhoto(
        @Path("photoId") photoId: String,
        @Body body: RequestBody,
    ): ApiResponse<Unit>

    @POST("photos/{photoId}/reactions/view")
    suspend fun viewUnseenReactions(
        @Path("photoId") photoId: String,
    ): ApiResponse<List<ReactionResponseDto>>

    @DELETE("photos/{photoId}")
    suspend fun deletePhoto(@Path("photoId") photoId: String)

    private companion object {
        const val DEFAULT_PAGE_SIZE = 10
    }
}
