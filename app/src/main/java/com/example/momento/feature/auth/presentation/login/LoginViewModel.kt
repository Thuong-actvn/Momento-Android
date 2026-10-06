package com.example.momento.feature.auth.presentation.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.feature.auth.data.firebase.GoogleSignIn
import com.example.momento.feature.auth.data.firebase.PhoneSignIn
import com.example.momento.feature.auth.domain.model.AuthUser
import com.example.momento.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val isBusy: Boolean = true,
    val otpRequested: Boolean = false,
    val codeSent: Boolean = false,
    val user: AuthUser? = null,
    val message: String = "Đang kiểm tra phiên đăng nhập...",
    val isError: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val googleSignIn: GoogleSignIn,
    private val phoneSignIn: PhoneSignIn,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()
    private var autoVerifying = false

    init {
        viewModelScope.launch {
            repository.restoreSession().fold(
                onSuccess = { user ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            user = user,
                            message = if (user == null) "Chọn Google hoặc nhập số điện thoại để đăng nhập."
                                else "Đã khôi phục tài khoản từ backend.",
                            isError = false,
                        )
                    }
                },
                onFailure = { showError(it) },
            )
        }
    }

    fun signInWithGoogle(activity: Activity) {
        if (_state.value.isBusy || _state.value.user != null) return
        _state.update {
            it.copy(isBusy = true, otpRequested = false, codeSent = false,
                message = "Đang đăng nhập với Google...", isError = false)
        }
        viewModelScope.launch { showSignInResult(googleSignIn.signIn(activity)) }
    }

    fun sendCode(activity: Activity, phoneNumber: String) {
        if (_state.value.isBusy || _state.value.user != null) return
        _state.update {
            it.copy(isBusy = true, otpRequested = true, codeSent = false,
                message = "Đang yêu cầu mã OTP từ Firebase. Bạn có thể nhập mã khi nhận được.", isError = false)
        }
        phoneSignIn.sendCode(
            activity = activity,
            phoneNumber = phoneNumber.trim(),
            onCodeSent = {
                if (!autoVerifying && _state.value.otpRequested && _state.value.user == null) {
                    _state.update {
                        it.copy(isBusy = false, codeSent = true, message = "Đã gửi mã. Nhập OTP để xác nhận.")
                    }
                }
            },
            onAutoVerified = { credential ->
                if (!autoVerifying && _state.value.user == null) {
                    autoVerifying = true
                    _state.update {
                        it.copy(isBusy = true, message = "Firebase đang tự xác thực số điện thoại...")
                    }
                    viewModelScope.launch {
                        showSignInResult(phoneSignIn.signInWithCredential(credential))
                        autoVerifying = false
                    }
                }
            },
            onError = { error ->
                if (!autoVerifying && _state.value.user == null) {
                    _state.update { it.copy(otpRequested = false, codeSent = false) }
                    showError(error)
                }
            },
        )
    }

    fun verifyCode(code: String) {
        if (_state.value.isBusy || !_state.value.codeSent || _state.value.user != null) return
        _state.update { it.copy(isBusy = true, message = "Đang xác nhận OTP...", isError = false) }
        viewModelScope.launch { showSignInResult(phoneSignIn.verifyCode(code.trim())) }
    }

    fun signOut() {
        if (_state.value.isBusy || _state.value.user == null) return
        _state.update { it.copy(isBusy = true, message = "Đang đăng xuất...", isError = false) }
        viewModelScope.launch {
            val result = repository.signOut()
            _state.update {
                it.copy(
                    isBusy = false,
                    otpRequested = false,
                    codeSent = false,
                    user = null,
                    message = if (result.isSuccess) "Đã đăng xuất."
                        else "Đã đăng xuất trên thiết bị; backend báo: ${result.exceptionOrNull()?.message}",
                    isError = result.isFailure,
                )
            }
        }
    }

    private fun showSignInResult(result: Result<AuthUser>) {
        result.fold(
            onSuccess = { user ->
                _state.update {
                    it.copy(isBusy = false, user = user, message = "Firebase và backend đã đăng nhập thành công.", isError = false)
                }
            },
            onFailure = { showError(it) },
        )
    }

    private fun showError(error: Throwable) {
        _state.update {
            it.copy(isBusy = false, message = error.message ?: "Đăng nhập thất bại.", isError = true)
        }
    }
}
