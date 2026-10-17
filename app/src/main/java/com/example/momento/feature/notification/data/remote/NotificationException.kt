package com.example.momento.feature.notification.data.remote

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import java.io.IOException
import java.time.format.DateTimeParseException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

enum class NotificationError {
    NETWORK,
    UNAUTHENTICATED,
    SESSION_CHANGED,
    INVALID_RESPONSE,
    REQUEST_FAILED,
    SERVER,
    UNKNOWN,
}

/** Presentation can localize the reason while retaining the backend code and HTTP status. */
class NotificationException(
    val reason: NotificationError,
    val backendCode: String? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null,
) : Exception(cause?.message ?: reason.name, cause)

fun Exception.toNotificationException(): NotificationException {
    if (this is NotificationException) return this
    val apiError = when (this) {
        is HttpException -> toApiException()
        is ApiException -> this
        else -> null
    }
    if (apiError != null) {
        val reason = when {
            apiError.statusCode == 401 || apiError.code in setOf(
                "UNAUTHORIZED", "FIREBASE_TOKEN_INVALID", "FIREBASE_TOKEN_EXPIRED",
            ) -> NotificationError.UNAUTHENTICATED
            apiError.statusCode in 500..599 -> NotificationError.SERVER
            else -> NotificationError.REQUEST_FAILED
        }
        return NotificationException(reason, apiError.code, apiError.statusCode, apiError)
    }
    val reason = when (this) {
        is SerializationException,
        is DateTimeParseException,
        is IllegalStateException -> NotificationError.INVALID_RESPONSE
        is IOException -> NotificationError.NETWORK
        is IllegalArgumentException -> NotificationError.REQUEST_FAILED
        else -> NotificationError.UNKNOWN
    }
    return NotificationException(reason = reason, cause = this)
}
