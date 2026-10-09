package com.example.momento.feature.profile.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.core.user.data.remote.dto.ProfileDto
import com.example.momento.feature.profile.data.remote.dto.AvatarUploadResponse
import com.example.momento.feature.profile.data.remote.dto.UpdateProfileRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part

interface ProfileApi {
    @GET("users/me")
    suspend fun me(): ApiResponse<ProfileDto>

    @PATCH("users/me")
    suspend fun update(@Body body: UpdateProfileRequest): ApiResponse<ProfileDto>

    @Multipart
    @POST("users/me/avatar/upload")
    suspend fun uploadAvatar(@Part image: MultipartBody.Part): ApiResponse<AvatarUploadResponse>
}
