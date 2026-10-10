package com.example.momento.feature.moment.data.remote

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

enum class MomentError {
    NETWORK,
    UNAUTHENTICATED,
    SESSION_CHANGED,
    INVALID_IMAGE,
    IMAGE_TOO_LARGE,
    INVALID_RESPONSE,
    REQUEST_FAILED,
    SERVER,
    UNKNOWN,
}

/** Keeps backend codes/statuses available while presentation localizes the reason. */
class MomentException(
    val reason: MomentError,
    val backendCode: String? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(cause?.message ?: reason.name, cause)

internal fun Exception.toMomentException(): MomentException {
    if (this is MomentException) return this
    val apiError = when (this) {
        is HttpException -> toApiException()
        is ApiException -> this
        else -> null
    }
    if (apiError != null) {
        val reason = when {
            apiError.statusCode == 401 || apiError.code in setOf(
                "UNAUTHORIZED", "FIREBASE_TOKEN_INVALID", "FIREBASE_TOKEN_EXPIRED",
            ) -> MomentError.UNAUTHENTICATED
            apiError.statusCode == 413 || apiError.code == "FILE_TOO_LARGE" -> MomentError.IMAGE_TOO_LARGE
            apiError.code == "INVALID_FILE_FORMAT" -> MomentError.INVALID_IMAGE
            apiError.statusCode in 500..599 -> MomentError.SERVER
            else -> MomentError.REQUEST_FAILED
        }
        return MomentException(reason, apiError.code, apiError.statusCode, apiError)
    }
    val reason = when (this) {
        is SerializationException -> MomentError.INVALID_RESPONSE
        is IOException -> MomentError.NETWORK
        is IllegalArgumentException -> MomentError.REQUEST_FAILED
        else -> MomentError.UNKNOWN
    }
    return MomentException(reason = reason, cause = this)
}
