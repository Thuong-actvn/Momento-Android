package com.example.momento.feature.chat.presentation.detail

import androidx.annotation.StringRes
import com.example.momento.R
import com.example.momento.feature.chat.data.remote.ChatError
import com.example.momento.feature.chat.data.remote.ChatException

@StringRes
fun chatHistoryError(error: Throwable): Int {
    val reason = (error as? ChatException)?.reason
    return when (reason) {
        ChatError.UNAUTHENTICATED, ChatError.SESSION_CHANGED -> R.string.chat_session_error
        ChatError.NETWORK -> R.string.chat_history_network_error
        ChatError.INVALID_RESPONSE -> R.string.chat_invalid_response
        ChatError.INVALID_INPUT -> R.string.chat_invalid_recipient
        ChatError.FORBIDDEN, ChatError.PHOTO_ACCESS_DENIED -> R.string.chat_history_forbidden
        else -> R.string.chat_history_error
    }
}

@StringRes
fun chatSendError(error: Throwable): Int {
    val exception = error as? ChatException
    if (exception?.isDeliveryUncertain == true) {
        return R.string.chat_send_uncertain
    }

    return when (exception?.reason) {
        ChatError.UNAUTHENTICATED, ChatError.SESSION_CHANGED -> R.string.chat_session_error
        ChatError.BLOCKED -> R.string.chat_send_blocked
        ChatError.RECIPIENT_DELETED -> R.string.chat_recipient_deleted
        ChatError.USER_NOT_FOUND -> R.string.chat_invalid_recipient
        ChatError.INVALID_INPUT -> R.string.chat_message_invalid
        ChatError.FORBIDDEN, ChatError.PHOTO_ACCESS_DENIED -> R.string.chat_send_forbidden
        ChatError.NETWORK -> R.string.chat_send_network_error
        else -> R.string.chat_send_error
    }
}
