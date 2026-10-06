package com.example.momento.feature.auth.data.remote

import com.example.momento.core.network.ApiResponse
import com.example.momento.core.network.NoAuth
import com.example.momento.feature.auth.data.remote.dto.DisplayNameRequest
import com.example.momento.feature.auth.data.remote.dto.LoginRequest
import com.example.momento.feature.auth.data.remote.dto.LoginResponse
import com.example.momento.feature.auth.data.remote.dto.UserDto
import com.example.momento.feature.auth.data.remote.dto.UsernameRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST

interface AuthApi {
    // Public login uses the token in its body, without an Authorization header.
    @NoAuth
    @POST("auth/login/google")
    suspend fun google(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @NoAuth
    @POST("auth/login/phone")
    suspend fun phone(@Body body: LoginRequest): ApiResponse<LoginResponse>

    @GET("users/me")
    suspend fun me(): ApiResponse<UserDto>

    @PATCH("users/me")
    suspend fun displayName(@Body body: DisplayNameRequest): ApiResponse<UserDto>

    @PATCH("users/me/username")
    suspend fun username(@Body body: UsernameRequest): ApiResponse<UserDto>

    @POST("auth/logout")
    suspend fun logout()
}
