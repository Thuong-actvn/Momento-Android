package com.example.momento.feature.chat.data.remote

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import java.io.IOException
import kotlinx.coroutines.CancellationException
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

internal fun <T> decodeChatResponse(block: () -> T): T {
    try {
        return block()
    } catch (error: CancellationException) {
        throw error
    } catch (error: ApiException) {
        throw error.toChatException()
    } catch (error: Exception) {
        throw ChatException(ChatError.INVALID_RESPONSE, cause = error)
    }
}

internal fun Exception.toChatException(sending: Boolean = false): ChatException {
    val error = when (this) {
        is ChatException -> this
        is HttpException -> toApiException().asChatError()
        is ApiException -> asChatError()
        is SerializationException -> ChatException(ChatError.INVALID_RESPONSE, cause = this)
        is IOException -> ChatException(ChatError.NETWORK, cause = this)
        is IllegalArgumentException -> ChatException(ChatError.INVALID_INPUT, cause = this)
        else -> ChatException(ChatError.UNKNOWN, cause = this)
    }
    if (!sending) {
        return error
    }

    val deliveryUncertain = when (error.reason) {
        ChatError.NETWORK, ChatError.INVALID_RESPONSE, ChatError.SERVER, ChatError.UNKNOWN -> true
        else -> error.statusCode == 408
    }
    if (!deliveryUncertain) {
        return error
    }
    return ChatException(
        reason = error.reason,
        backendCode = error.backendCode,
        statusCode = error.statusCode,
        isDeliveryUncertain = true,
        cause = error,
    )
}

private fun ApiException.asChatError(): ChatException {
    val reason = when {
        statusCode == 401 -> ChatError.UNAUTHENTICATED
        code in setOf("UNAUTHORIZED", "FIREBASE_TOKEN_INVALID", "FIREBASE_TOKEN_EXPIRED") -> ChatError.UNAUTHENTICATED
        code in setOf("CONVERSATION_MESSAGE_BLOCKED", "USER_BLOCKED") -> ChatError.BLOCKED
        code == "RECIPIENT_ACCOUNT_DELETED" -> ChatError.RECIPIENT_DELETED
        code == "USER_NOT_FOUND" -> ChatError.USER_NOT_FOUND
        code == "PHOTO_NOT_FOUND" -> ChatError.PHOTO_NOT_FOUND
        code == "PHOTO_ACCESS_DENIED" -> ChatError.PHOTO_ACCESS_DENIED
        code == "RESOURCE_NOT_FOUND" -> ChatError.CONVERSATION_NOT_FOUND
        code == "VALIDATION_ERROR" -> ChatError.INVALID_INPUT
        statusCode == 403 -> ChatError.FORBIDDEN
        statusCode in 500..599 || code == "INTERNAL_ERROR" -> ChatError.SERVER
        else -> ChatError.REQUEST_FAILED
    }
    return ChatException(reason, code, statusCode, cause = this)
}
