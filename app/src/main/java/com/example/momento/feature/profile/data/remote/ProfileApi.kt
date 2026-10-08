package com.example.momento.feature.profile.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.core.user.data.remote.dto.ProfileDto
import com.example.momento.feature.profile.data.remote.dto.UpdateProfileRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

interface ProfileApi {
    @GET("users/me")
    suspend fun me(): ApiResponse<ProfileDto>

    @PATCH("users/me")
    suspend fun update(@Body body: UpdateProfileRequest): ApiResponse<ProfileDto>
}
