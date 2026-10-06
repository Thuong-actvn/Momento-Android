package com.example.momento.core.network

import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
) {
    fun requireData(): T {
        check(success) { error?.message ?: "Backend request failed" }
        return checkNotNull(data) { "Backend response has no data" }
    }
}

@Serializable
data class ApiError(val code: String, val message: String)
