package com.example.momento.feature.moment.presentation.camera

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.data.remote.FriendError
import com.example.momento.feature.friend.data.remote.FriendException
import com.example.momento.feature.friend.domain.repository.FriendRepository
import com.example.momento.feature.moment.data.local.InvalidMomentImageException
import com.example.momento.feature.moment.data.local.MomentImageStore
import com.example.momento.feature.moment.data.local.PreparedMomentImage
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns the draft; camera hardware and library permissions stay in the route. */
@HiltViewModel
class CameraViewModel @Inject constructor(
    private val images: MomentImageStore,
    private val savedState: SavedStateHandle,
    private val friends: FriendRepository,
    private val moments: MomentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(restoreState())
    val state = _state.asStateFlow()

    private var pendingCameraFile: File? = null
    private var imageJob: Job? = null

    // Guard concurrent image operations without exposing a loading state to the UI.
    private val hasImageOperation: Boolean get() = pendingCameraFile != null || imageJob?.isActive == true

    private fun restoreState(): CameraUiState {
        val path = savedState.get<String>("draftPath")
        val draftImage = if (path != null && File(path).isFile) {
            File(path).toURI().toString()
        } else {
            null
        }
        val caption = if (draftImage != null) savedState.get<String>("caption") ?: "" else ""

        val audienceName = savedState.get<String>("audience")
        val audience = if (audienceName == null) {
            CameraAudience.ALL_FRIENDS
        } else {
            CameraAudience.valueOf(audienceName)
        }
        val friendIds = savedState.get<ArrayList<String>>("friendIds")?.toSet() ?: emptySet()

        return CameraUiState(
            draftImage = draftImage,
            caption = caption,
            selection = CameraAudienceSelection(audience, friendIds),
        )
    }

    fun selectAudience(audience: CameraAudience) {
        if (_state.value.isSending) return
        updateSelection(_state.value.selection.select(audience))
    }

    fun toggleFriend(id: String) {
        val current = _state.value
        val isFriend = current.recipients.any { recipient -> recipient.id == id }
        if (current.isSending || !isFriend) return
        updateSelection(current.selection.toggleFriend(id))
    }

    private fun updateSelection(selection: CameraAudienceSelection) {
        savedState["audience"] = selection.audience.name
        savedState["friendIds"] = ArrayList(selection.friendIds)
        _state.update { it.copy(selection = selection) }
    }

    fun refreshRecipients() {
        val current = _state.value
        if (current.isLoadingRecipients || current.isSending) return

        _state.update { it.copy(isLoadingRecipients = true, recipientsError = null) }
        viewModelScope.launch {
            friends.getFriends()
                .onSuccess { profiles -> showRecipients(profiles) }
                .onFailure { error -> showRecipientsError(error) }
        }
    }

    private fun showRecipients(profiles: List<PublicProfile>) {
        val uniqueProfiles = profiles.distinctBy { profile -> profile.id }
        val recipients = uniqueProfiles.map { profile ->
            val name = if (profile.displayName.isNullOrBlank()) profile.username else profile.displayName
            CameraRecipient(id = profile.id, name = name, avatarUrl = profile.avatarUrl)
        }

        val availableIds = recipients.map { recipient -> recipient.id }.toSet()
        val selection = _state.value.selection
        val selectedIds = selection.friendIds.filter { id -> id in availableIds }.toSet()
        // Chỉ bỏ ID không còn là bạn bè; giữ nguyên nhóm người nhận đang chọn.
        updateSelection(selection.copy(friendIds = selectedIds))

        _state.update { current ->
            current.copy(
                recipients = recipients,
                isLoadingRecipients = false,
                hasLoadedRecipients = true,
                recipientsError = null,
            )
        }
    }

    private fun showRecipientsError(error: Throwable) {
        val reason = (error as? FriendException)?.reason
        val message = when (reason) {
            FriendError.UNAUTHENTICATED, FriendError.SESSION_CHANGED -> R.string.camera_session_error
            FriendError.NETWORK -> R.string.camera_recipients_network_error
            else -> R.string.camera_recipients_error
        }
        _state.update { it.copy(isLoadingRecipients = false, recipientsError = message) }
    }

    fun changeCaption(value: String) {
        val current = _state.value
        if (!current.hasDraft || current.isSending) return
        val caption = value.limitMomentCaption()
        savedState["caption"] = caption
        _state.update { it.copy(caption = caption) }
    }

    fun beginCapture(): File? {
        val current = _state.value
        if (hasImageOperation || current.isSending || current.hasDraft) return null
        val file = try {
            images.createCameraFile()
        } catch (_: Exception) {
            captureFailed()
            return null
        }
        pendingCameraFile = file
        _state.update { it.copy(error = null) }
        return file
    }

    fun captureSucceeded(file: File) {
        if (pendingCameraFile != file) {
            images.deleteCameraFile(file)
            return
        }
        prepareImage(Uri.fromFile(file), file)
    }

    fun captureFailed() {
        images.deleteCameraFile(pendingCameraFile)
        pendingCameraFile = null
        _state.update { it.copy(error = R.string.camera_capture_error) }
    }

    fun chooseImage(uri: Uri) {
        if (hasImageOperation || _state.value.isSending) return
        prepareImage(uri)
    }

    private fun prepareImage(uri: Uri, cameraFile: File? = null) {
        _state.update { it.copy(error = null) }
        imageJob = viewModelScope.launch {
            try {
                val image = images.prepare(uri)
                saveDraft(image)
            } catch (error: CancellationException) {
                // Hủy thao tác khi bỏ nháp hoặc rời màn hình không phải là lỗi đọc ảnh.
                throw error
            } catch (error: Exception) {
                _state.update { it.copy(error = imageError(error)) }
            } finally {
                images.deleteCameraFile(cameraFile)
                if (pendingCameraFile == cameraFile) pendingCameraFile = null
            }
        }
    }

    private fun saveDraft(image: PreparedMomentImage) {
        images.deleteDraft(savedState["draftPath"])
        savedState["draftPath"] = image.file.absolutePath
        savedState["draftMime"] = image.mimeType
        _state.update { it.copy(draftImage = image.file.toURI().toString(), error = null) }
    }

    fun sendMoment() {
        val form = _state.value
        if (!form.canSend || hasImageOperation) return

        val path = savedState.get<String>("draftPath")
        val mimeType = savedState.get<String>("draftMime")
        if (path == null || mimeType == null) {
            _state.update { it.copy(error = R.string.camera_image_read_error) }
            return
        }

        _state.update { it.copy(isSending = true, error = null, successMessage = null) }
        viewModelScope.launch {
            // Dùng thông tin tại thời điểm bấm gửi cho toàn bộ request.
            moments.uploadMoment(
                image = File(path),
                mimeType = mimeType,
                audienceType = form.selection.apiAudienceType,
                caption = form.caption,
                audienceUserIds = form.selection.apiUserIds,
            )
                .onSuccess { sendSucceeded(form.selection.audience) }
                .onFailure { error ->
                    _state.update { it.copy(isSending = false, error = sendError(error)) }
                }
        }
    }

    private fun sendSucceeded(audience: CameraAudience) {
        clearDraft()
        val message = if (audience == CameraAudience.ONLY_ME) {
            R.string.camera_saved_private
        } else {
            R.string.camera_sent
        }
        _state.update { it.copy(isSending = false, successMessage = message) }
    }

    fun clearSuccessMessage() {
        _state.update { it.copy(successMessage = null) }
    }

    fun discardDraft() {
        if (_state.value.isSending) return
        imageJob?.cancel()
        clearDraft()
    }

    private fun clearDraft() {
        images.deleteDraft(savedState["draftPath"])
        savedState["draftPath"] = null
        savedState["draftMime"] = null
        savedState["caption"] = ""
        _state.update { it.copy(draftImage = null, caption = "", error = null) }
    }

    private fun imageError(error: Throwable): Int = when {
        error is InvalidMomentImageException && error.tooLarge -> R.string.camera_image_too_large
        error is InvalidMomentImageException -> R.string.camera_image_invalid
        else -> R.string.camera_image_read_error
    }

    private fun sendError(error: Throwable): Int = when ((error as? MomentException)?.reason) {
        MomentError.UNAUTHENTICATED, MomentError.SESSION_CHANGED -> R.string.camera_session_error
        MomentError.INVALID_IMAGE -> R.string.camera_image_invalid
        MomentError.IMAGE_TOO_LARGE -> R.string.camera_image_too_large
        MomentError.NETWORK, MomentError.INVALID_RESPONSE -> R.string.camera_send_unconfirmed
        else -> R.string.camera_send_error
    }

    override fun onCleared() {
        images.deleteDraft(savedState["draftPath"])
        images.deleteCameraFile(pendingCameraFile)
    }
}
