package com.example.momento.feature.auth.presentation.completeprofile

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

internal val UsernamePattern = Regex("^[a-z0-9_]{3,20}$")

enum class UsernameAvailability { Unchecked, Checking, Available, Unavailable }

data class CompleteProfileUiState(
    val displayName: String = "",
    val username: String = "",
    val usernameAvailability: UsernameAvailability = UsernameAvailability.Unchecked,
    val usernameCheckError: String? = null,
    val error: String? = null,
    val isSubmitting: Boolean = false,
    val isCompleted: Boolean = false,
) {
    val canSubmit: Boolean
        get() {
            val name = displayName.trim()
            return !isSubmitting && !isCompleted &&
                name.isNotEmpty() && name.length <= 50 && UsernamePattern.matches(username) &&
                usernameAvailability != UsernameAvailability.Unavailable &&
                usernameAvailability != UsernameAvailability.Checking
        }
}

@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        CompleteProfileUiState(
            displayName = savedStateHandle["displayName"] ?: "",
            username = savedStateHandle["username"] ?: "",
        ),
    )
    val state = _state.asStateFlow()
    // Hủy lần kiểm tra cũ khi người dùng tiếp tục nhập.
    private var checkJob: Job? = null
    // Đánh số để bỏ qua kết quả kiểm tra cũ trả về muộn.
    private var checkVersion = 0L

    init {
        if (UsernamePattern.matches(_state.value.username)) {
            checkUsername()
        }
    }

    fun onDisplayNameChange(input: String) {
        if (!canEdit()) return
        val name = input.take(50)
        savedStateHandle["displayName"] = name
        _state.update { it.copy(displayName = name, error = null) }
    }

    fun onUsernameChange(input: String) {
        if (!canEdit()) return
        val username = input.lowercase(Locale.ROOT).take(20)
        if (username == _state.value.username) return
        savedStateHandle["username"] = username
        _state.update { it.copy(username = username, error = null) }
        checkUsername()
    }

    fun retryUsernameCheck() {
        if (canEdit()) checkUsername(immediate = true)
    }

    private fun checkUsername(immediate: Boolean = false) {
        checkJob?.cancel()
        val version = ++checkVersion
        val username = _state.value.username
        val valid = UsernamePattern.matches(username)
        _state.update {
            it.copy(
                usernameAvailability = if (valid) UsernameAvailability.Checking else UsernameAvailability.Unchecked,
                usernameCheckError = null,
            )
        }
        if (!valid) return
        checkJob = viewModelScope.launch {
            if (!immediate) delay(400)
            val result = repository.checkUsernameAvailability(username)
            // Ignore responses for an older input or a form that is already being saved.
            if (version != checkVersion || !canEdit()) return@launch
            if (result.isSuccess) {
                val available = result.getOrNull() == true
                _state.update {
                    it.copy(
                        usernameAvailability = if (available) UsernameAvailability.Available
                            else UsernameAvailability.Unavailable,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        usernameAvailability = UsernameAvailability.Unchecked,
                        usernameCheckError = "Không thể kiểm tra username. Vui lòng thử lại.",
                    )
                }
            }
        }
    }

    fun completeProfile() {
        val form = _state.value
        if (!form.canSubmit) return
        checkJob?.cancel()
        checkVersion++
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            val result = repository.completeProfile(form.displayName, form.username)
            if (result.isSuccess) {
                _state.update { it.copy(isSubmitting = false, isCompleted = true) }
            } else {
                val error = result.exceptionOrNull()
                val taken = error is HttpException && error.code() == 409
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        usernameAvailability = if (taken) UsernameAvailability.Unavailable else it.usernameAvailability,
                        error = if (taken) "Username này đã được sử dụng."
                            else "Không thể lưu hồ sơ. Vui lòng thử lại.",
                    )
                }
            }
        }
    }

    private fun canEdit(): Boolean {
        val current = _state.value
        return !current.isSubmitting && !current.isCompleted
    }
}
