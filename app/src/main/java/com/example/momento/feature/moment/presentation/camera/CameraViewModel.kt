package com.example.momento.feature.moment.presentation.camera

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.moment.data.local.InvalidMomentImageException
import com.example.momento.feature.moment.data.local.MomentImageStore
import com.example.momento.feature.moment.data.local.PreparedMomentImage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
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
) : ViewModel() {
    private val restoredPath = savedState.get<String>("draftPath")?.takeIf { File(it).isFile }
    private val _state = MutableStateFlow(CameraUiState(
        draftImage = restoredPath?.let { File(it).toURI().toString() },
        caption = if (restoredPath != null) savedState["caption"] ?: "" else "",
        selection = CameraAudienceSelection(savedState.get<String>("audience")?.let(CameraAudience::valueOf)
            ?: CameraAudience.ALL_FRIENDS),
    ))
    val state = _state.asStateFlow()
    private var pendingCameraFile: File? = null
    private var imageJob: Job? = null

    // Guard concurrent image operations without exposing a loading state to the UI.
    private val hasImageOperation: Boolean get() = pendingCameraFile != null || imageJob?.isActive == true

    fun selectAudience(audience: CameraAudience) {
        if (_state.value.isSending) return
        savedState["audience"] = audience.name
        _state.update { it.copy(selection = it.selection.select(audience)) }
    }

    fun changeCaption(value: String) {
        if (!_state.value.hasDraft || _state.value.isSending) return
        val caption = value.limitMomentCaption()
        savedState["caption"] = caption
        _state.update { it.copy(caption = caption) }
    }

    fun beginCapture(): File? {
        if (hasImageOperation || _state.value.isSending || _state.value.hasDraft) return null
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
                val image = suspendRunCatching { images.prepare(uri) }.getOrElse { error ->
                    _state.update { it.copy(error = imageError(error)) }
                    return@launch
                }
                saveDraft(image)
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

    fun discardDraft() {
        if (_state.value.isSending) return
        imageJob?.cancel()
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

    override fun onCleared() {
        images.deleteDraft(savedState["draftPath"])
        images.deleteCameraFile(pendingCameraFile)
    }
}
