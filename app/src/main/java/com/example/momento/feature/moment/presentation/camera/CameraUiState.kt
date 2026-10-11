package com.example.momento.feature.moment.presentation.camera

import androidx.annotation.StringRes
import com.example.momento.feature.moment.domain.model.AudienceType

enum class CameraAudience { ALL_FRIENDS, ONLY_ME, SELECTED_FRIENDS }

enum class CameraPreviewStatus { UNAVAILABLE, PERMISSION_REQUIRED, PERMISSION_DENIED, READY, ERROR }

data class CameraRecipient(val id: String, val name: String, val avatarUrl: String? = null)

/** ONLY_ME deliberately uses CUSTOM with no viewers, as supported by PhotoServiceImpl. */
data class CameraAudienceSelection(
    val audience: CameraAudience = CameraAudience.ALL_FRIENDS,
    val friendIds: Set<String> = emptySet(),
) {
    val apiAudienceType: AudienceType
        get() = if (audience == CameraAudience.ALL_FRIENDS) AudienceType.ALL_FRIENDS else AudienceType.CUSTOM

    val apiUserIds: List<String>
        get() = if (audience == CameraAudience.SELECTED_FRIENDS) friendIds.toList() else emptyList()

    val isValid: Boolean
        get() = audience != CameraAudience.SELECTED_FRIENDS || friendIds.isNotEmpty()

    fun select(audience: CameraAudience) = CameraAudienceSelection(audience)

    fun toggleFriend(id: String): CameraAudienceSelection {
        val current = if (audience == CameraAudience.SELECTED_FRIENDS) friendIds else emptySet()
        val updated = if (id in current) current - id else current + id
        // Deselecting the last friend must never silently switch to ALL_FRIENDS or ONLY_ME.
        return CameraAudienceSelection(CameraAudience.SELECTED_FRIENDS, updated)
    }
}

data class CameraUiState(
    val draftImage: String? = null,
    val caption: String = "",
    val selection: CameraAudienceSelection = CameraAudienceSelection(),
    val recipients: List<CameraRecipient> = emptyList(),
    val isLoadingRecipients: Boolean = false,
    val hasLoadedRecipients: Boolean = false,
    @param:StringRes val recipientsError: Int? = null,
    val previewStatus: CameraPreviewStatus? = null,
    val flashEnabled: Boolean = false,
    val hasFlash: Boolean = false,
    val canSwitchCamera: Boolean = false,
    val isFrontCamera: Boolean = false,
    val zoomRatio: Float = 1f,
    val supportedZoomRatios: List<Float> = emptyList(),
    val latestImage: String? = null,
    val isSending: Boolean = false,
    @param:StringRes val error: Int? = null,
    @param:StringRes val successMessage: Int? = null,
) {
    val hasDraft: Boolean get() = draftImage != null
    val canCapture: Boolean get() = !hasDraft && previewStatus == CameraPreviewStatus.READY && !isSending
    val canSend: Boolean
        get() {
            if (!hasDraft || isSending || !selection.isValid) return false

            // Tất cả bạn bè và chỉ mình tôi không cần danh sách ID người nhận.
            if (selection.audience != CameraAudience.SELECTED_FRIENDS) return true

            if (!hasLoadedRecipients || isLoadingRecipients || recipientsError != null) return false
            val availableIds = recipients.map { recipient -> recipient.id }
            return availableIds.containsAll(selection.friendIds)
        }
}

const val MOMENT_CAPTION_LIMIT = 40

/** Count Unicode code points so a supplementary emoji does not consume two characters. */
fun String.momentCharacterCount(): Int = codePointCount(0, length)

fun String.limitMomentCaption(): String = if (momentCharacterCount() <= MOMENT_CAPTION_LIMIT) this
else substring(0, offsetByCodePoints(0, MOMENT_CAPTION_LIMIT))
