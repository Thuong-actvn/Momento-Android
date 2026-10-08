package com.example.momento.feature.auth.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.core.network.NoAuth
import com.example.momento.feature.auth.data.remote.dto.CompleteProfileRequest
import com.example.momento.feature.auth.data.remote.dto.LoginRequest
import com.example.momento.feature.auth.data.remote.dto.LoginResponse
import com.example.momento.feature.auth.data.remote.dto.ProfileDto
import com.example.momento.feature.auth.data.remote.dto.UsernameAvailabilityDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthApi {
    // Public login uses the token in its body, without an Authorization header.
    @NoAuth
    @POST("auth/login/google")
    suspend fun google(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @NoAuth
    @POST("auth/login/phone")
    suspend fun phone(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @GET("users/me")
    suspend fun me(): ApiResponse<ProfileDto>

    @PATCH("users/me/complete-profile")
    suspend fun completeProfile(@Body body: CompleteProfileRequest): ApiResponse<ProfileDto>

    @GET("users/username-availability")
    suspend fun checkUsernameAvailability(@Query("username") username: String): ApiResponse<UsernameAvailabilityDto>

    @POST("auth/logout")
    suspend fun logout()
}
