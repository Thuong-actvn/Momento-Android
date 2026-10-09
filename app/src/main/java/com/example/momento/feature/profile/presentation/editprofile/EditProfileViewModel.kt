package com.example.momento.feature.profile.presentation.editprofile

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.network.ApiException
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.profile.data.local.AvatarImageStore
import com.example.momento.feature.profile.data.local.InvalidAvatarException
import com.example.momento.feature.profile.domain.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val user: CurrentUser? = null,
    val displayName: String = "",
    val birthday: LocalDate? = null,
    val avatarPath: String? = null,
    val isLoading: Boolean = false,
    val isPreparingImage: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: Int? = null,
) {
    val isBusy get() = isLoading || isPreparingImage || isSaving || isSaved
    val hasChanges get() = user?.let {
        displayName.trim() != it.displayName.orEmpty().trim() || birthday != it.birthday || avatarPath != null
    } == true
    val nameError: Int?
        get() = when {
            displayName.trim() == user?.displayName.orEmpty().trim() -> null
            displayName.isBlank() -> R.string.profile_name_required
            displayName.trim().length > 50 -> R.string.profile_name_too_long
            else -> null
        }
    val canSave get() = !isBusy && hasChanges && nameError == null &&
        (birthday == null || birthday.isBefore(LocalDate.now()))
    val avatarPreview: String?
        get() = avatarPath?.let { File(it).toURI().toString() } ?: user?.avatarUrl
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val repository: ProfileRepository,
    private val images: AvatarImageStore,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state = _state.asStateFlow()
    private var initialized = false

    init {
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                if (user != null && (!initialized || _state.value.user?.id != user.id)) {
                    restoreDraft(user)
                } else {
                    _state.update {
                        it.copy(
                            user = user,
                            displayName = if (user != null && savedState.get<Boolean>("nameEdited") != true)
                                user.displayName.orEmpty() else it.displayName,
                            birthday = if (user != null && savedState.get<Boolean>("birthdayEdited") != true)
                                user.birthday else it.birthday,
                        )
                    }
                }
            }
        }
        if (repository.currentUser.value == null) refreshProfile()
    }

    private fun restoreDraft(user: CurrentUser) {
        if (savedState.get<String>("draftUserId") != user.id) {
            images.deleteDraft(savedState["avatarPath"])
            savedState["draftUserId"] = user.id
            savedState["displayName"] = user.displayName.orEmpty()
            savedState["birthday"] = user.birthday?.toString()
            savedState["nameEdited"] = false
            savedState["birthdayEdited"] = false
            savedState["avatarPath"] = null
            savedState["avatarMime"] = null
            savedState["uploadedAvatarUrl"] = null
        }
        val avatarPath = savedState.get<String>("avatarPath")?.takeIf { File(it).isFile }
        if (avatarPath == null) {
            savedState["avatarPath"] = null
            savedState["uploadedAvatarUrl"] = null
        }
        _state.update {
            it.copy(
                user = user,
                displayName = if (savedState.get<Boolean>("nameEdited") == true)
                    savedState["displayName"] ?: user.displayName.orEmpty() else user.displayName.orEmpty(),
                birthday = if (savedState.get<Boolean>("birthdayEdited") == true)
                    savedState.get<String>("birthday")?.let(LocalDate::parse) else user.birthday,
                avatarPath = avatarPath,
            )
        }
        initialized = true
    }

    fun refreshProfile() {
        if (_state.value.isBusy) return
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.refreshProfile()
            _state.update {
                it.copy(isLoading = false, error = if (result.isFailure) R.string.profile_load_error else null)
            }
        }
    }

    fun onDisplayNameChanged(value: String) {
        if (_state.value.isBusy) return
        savedState["displayName"] = value
        savedState["nameEdited"] = value.trim() != _state.value.user?.displayName.orEmpty().trim()
        _state.update { it.copy(displayName = value, error = null) }
    }

    fun onBirthdayChanged(value: LocalDate) {
        if (_state.value.isBusy || !value.isBefore(LocalDate.now())) return
        savedState["birthday"] = value.toString()
        savedState["birthdayEdited"] = value != _state.value.user?.birthday
        _state.update { it.copy(birthday = value, error = null) }
    }

    fun chooseAvatar(uri: Uri) {
        if (_state.value.isBusy) return
        prepareAvatar(uri)
    }

    fun createCameraUri(): Uri? {
        if (_state.value.isBusy) return null
        return runCatching {
            images.deleteCamera(pendingCamera())
            images.createCameraUri().also { savedState["cameraUri"] = it.toString() }
        }.getOrElse {
            reportImageSourceError()
            null
        }
    }

    fun onCameraResult(success: Boolean) {
        val uri = pendingCamera() ?: return
        if (success && !_state.value.isBusy) {
            prepareAvatar(uri, fromCamera = true)
        } else {
            images.deleteCamera(uri)
            savedState["cameraUri"] = null
        }
    }

    fun reportImageSourceError() {
        _state.update { it.copy(error = R.string.profile_image_source_error) }
    }

    private fun prepareAvatar(uri: Uri, fromCamera: Boolean = false) {
        _state.update { it.copy(isPreparingImage = true, error = null) }
        viewModelScope.launch {
            try {
                val result = suspendRunCatching { images.prepare(uri) }
                result.onSuccess { image ->
                    images.deleteDraft(_state.value.avatarPath)
                    savedState["avatarPath"] = image.file.absolutePath
                    savedState["avatarMime"] = image.mimeType
                    savedState["uploadedAvatarUrl"] = null
                    _state.update { it.copy(avatarPath = image.file.absolutePath) }
                }
                _state.update {
                    it.copy(isPreparingImage = false, error = result.exceptionOrNull()?.let(::imageError))
                }
            } finally {
                if (fromCamera) {
                    images.deleteCamera(uri)
                    savedState["cameraUri"] = null
                }
            }
        }
    }

    fun save() {
        val form = _state.value
        if (!form.canSave) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = suspendRunCatching {
                val avatarUrl = form.avatarPath?.let { path ->
                    savedState.get<String>("uploadedAvatarUrl") ?: repository.uploadAvatar(
                        image = File(path), mimeType = checkNotNull(savedState["avatarMime"]),
                    ).getOrThrow().also { savedState["uploadedAvatarUrl"] = it }
                }
                val current = checkNotNull(repository.currentUser.value)
                check(current.id == form.user?.id) { "User session has changed" }
                repository.updateProfile(
                    displayName = form.displayName.trim().takeIf { it != form.user?.displayName.orEmpty().trim() },
                    birthday = form.birthday?.takeIf { it != form.user?.birthday },
                    avatarUrl = avatarUrl?.takeIf { it != current.avatarUrl },
                ).getOrThrow()
            }
            if (result.isSuccess) {
                images.deleteDraft(form.avatarPath)
                savedState["avatarPath"] = null
                savedState["uploadedAvatarUrl"] = null
            }
            _state.update {
                it.copy(
                    isSaving = false, isSaved = result.isSuccess,
                    error = result.exceptionOrNull()?.let(::saveError),
                )
            }
        }
    }

    private fun pendingCamera(): Uri? = savedState.get<String>("cameraUri")?.let(Uri::parse)

    private fun imageError(error: Throwable): Int = when {
        error is InvalidAvatarException && error.tooLarge -> R.string.profile_image_too_large
        error is InvalidAvatarException -> R.string.profile_image_invalid
        else -> R.string.profile_image_read_error
    }

    private fun saveError(error: Throwable): Int = when ((error as? ApiException)?.statusCode) {
        401 -> R.string.profile_session_error
        413 -> R.string.profile_image_too_large
        415 -> R.string.profile_image_invalid
        else -> R.string.profile_save_error
    }

    override fun onCleared() {
        images.deleteDraft(_state.value.avatarPath)
        images.deleteCamera(pendingCamera())
    }
}
