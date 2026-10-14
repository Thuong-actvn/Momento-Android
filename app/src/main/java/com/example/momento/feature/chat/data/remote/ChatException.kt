package com.example.momento.feature.chat.data.remote

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

enum class ChatError {
    NETWORK,
    UNAUTHENTICATED,
    SESSION_CHANGED,
    INVALID_INPUT,
    INVALID_RESPONSE,
    BLOCKED,
    RECIPIENT_DELETED,
    USER_NOT_FOUND,
    PHOTO_NOT_FOUND,
    PHOTO_ACCESS_DENIED,
    CONVERSATION_NOT_FOUND,
    FORBIDDEN,
    REQUEST_FAILED,
    SERVER,
    UNKNOWN,
}

class ChatException(
    val reason: ChatError,
    val backendCode: String? = null,
    val statusCode: Int? = null,
    /** A send may have reached the server. Refresh history before deliberately sending again. */
    val isDeliveryUncertain: Boolean = false,
    cause: Throwable? = null,
) : Exception(cause?.message ?: reason.name, cause)

internal fun Exception.toChatException(sending: Boolean = false): ChatException {
    val error = if (this is ChatException) {
        this
    } else {
        val api = when (this) {
            is HttpException -> toApiException()
            is ApiException -> this
            else -> null
        }
        val reason = if (api != null) {
            when {
                api.statusCode == 401 || api.code in setOf(
                    "UNAUTHORIZED", "FIREBASE_TOKEN_INVALID", "FIREBASE_TOKEN_EXPIRED",
                ) -> ChatError.UNAUTHENTICATED
                api.code in setOf("CONVERSATION_MESSAGE_BLOCKED", "USER_BLOCKED") -> ChatError.BLOCKED
                api.code == "RECIPIENT_ACCOUNT_DELETED" -> ChatError.RECIPIENT_DELETED
                api.code == "USER_NOT_FOUND" -> ChatError.USER_NOT_FOUND
                api.code == "PHOTO_NOT_FOUND" -> ChatError.PHOTO_NOT_FOUND
                api.code == "PHOTO_ACCESS_DENIED" -> ChatError.PHOTO_ACCESS_DENIED
                api.code == "RESOURCE_NOT_FOUND" -> ChatError.CONVERSATION_NOT_FOUND
                api.code == "VALIDATION_ERROR" -> ChatError.INVALID_INPUT
                api.statusCode == 403 -> ChatError.FORBIDDEN
                api.statusCode in 500..599 || api.code == "INTERNAL_ERROR" -> ChatError.SERVER
                else -> ChatError.REQUEST_FAILED
            }
        } else {
            when (this) {
                is SerializationException -> ChatError.INVALID_RESPONSE
                is IOException -> ChatError.NETWORK
                is IllegalArgumentException -> ChatError.INVALID_INPUT
                else -> ChatError.UNKNOWN
            }
        }
        ChatException(reason, api?.code, api?.statusCode, cause = api ?: this)
    }
    if (!sending || (error.statusCode != 408 && error.reason !in setOf(
            ChatError.NETWORK, ChatError.INVALID_RESPONSE, ChatError.SERVER, ChatError.UNKNOWN,
        ))
    ) return error
    return ChatException(
        error.reason, error.backendCode, error.statusCode,
        isDeliveryUncertain = true, cause = error,
    )
}
