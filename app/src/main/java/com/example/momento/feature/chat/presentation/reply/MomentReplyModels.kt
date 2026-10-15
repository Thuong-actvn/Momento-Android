package com.example.momento.feature.chat.presentation.reply

import androidx.annotation.StringRes
import com.example.momento.feature.chat.presentation.detail.CHAT_MESSAGE_MAX_LENGTH

data class MomentReplyUiState(
    val ownUserId: String? = null,
    val sessionVersion: Long? = null,
    val photoId: String? = null,
    val photoOwnerId: String? = null,
    val draft: String = "",
    val isSending: Boolean = false,
    val isCheckingDelivery: Boolean = false,
    val isDeliveryUncertain: Boolean = false,
    @param:StringRes val error: Int? = null,
    val notice: MomentReplyNotice? = null,
    val conversationToOpen: String? = null,
) {
    val isOwnMoment: Boolean
        get() = ownUserId != null && photoOwnerId == ownUserId

    val canReply: Boolean
        get() = ownUserId != null && photoId != null && photoOwnerId != null && !isOwnMoment

    val isDraftTooLong: Boolean
        get() = draft.trim().length > CHAT_MESSAGE_MAX_LENGTH

    val canSend: Boolean
        get() = canReply && draft.isNotBlank() && !isDraftTooLong &&
            !isSending && !isCheckingDelivery && !isDeliveryUncertain
}

class MomentReplyNotice(val otherUserId: String)
