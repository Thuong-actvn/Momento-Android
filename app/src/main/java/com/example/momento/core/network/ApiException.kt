package com.example.momento.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException

class ApiException(
    val code: String?,
    override val message: String,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(message, cause)

private val errorJson = Json { ignoreUnknownKeys = true }

/** Keep the status even when an HTTP error contains HTML or an empty body. */
fun HttpException.toApiException(): ApiException {
    val error = runCatching {
        response()?.errorBody()?.string()?.let {
            errorJson.decodeFromString<ApiResponse<JsonElement>>(it).error
        }
    }.getOrNull()

    return ApiException(
        code = error?.code,
        message = error?.message ?: message(),
        statusCode = code(),
        cause = this,
    )
}
