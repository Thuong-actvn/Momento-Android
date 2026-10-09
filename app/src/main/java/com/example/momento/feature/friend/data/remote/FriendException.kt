package com.example.momento.feature.friend.data.remote

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import java.io.IOException
import retrofit2.HttpException

enum class FriendError {
    NETWORK,
    UNAUTHENTICATED,
    SESSION_CHANGED,
    REQUEST_FAILED,
    SERVER,
    UNKNOWN,
}

/** Presentation can localize reason without losing backend details for diagnostics. */
class FriendException(
    val reason: FriendError,
    val backendCode: String? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(cause?.message ?: reason.name, cause)

internal fun Exception.toFriendException(): FriendException {
    if (this is FriendException) return this
    val apiError = when (this) {
        is HttpException -> toApiException()
        is ApiException -> this
        else -> null
    }
    if (apiError != null) {
        val reason = when (apiError.statusCode) {
            401 -> FriendError.UNAUTHENTICATED
            in 500..599 -> FriendError.SERVER
            else -> FriendError.REQUEST_FAILED
        }
        return FriendException(reason, apiError.code, apiError.statusCode, apiError)
    }
    val reason = when (this) {
        is IOException -> FriendError.NETWORK
        is IllegalArgumentException -> FriendError.REQUEST_FAILED
        else -> FriendError.UNKNOWN
    }
    return FriendException(reason = reason, cause = this)
}
