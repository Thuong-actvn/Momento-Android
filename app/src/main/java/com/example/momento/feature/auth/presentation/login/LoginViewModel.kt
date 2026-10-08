package com.example.momento.feature.auth.presentation.login

import android.app.Activity
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.feature.auth.data.firebase.GoogleSignIn
import com.example.momento.feature.auth.data.firebase.PhoneSignIn
import com.example.momento.core.user.domain.model.CurrentUser
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
    val user: CurrentUser? = null,
    val message: String = "Đang kiểm tra phiên đăng nhập...",
    val isError: Boolean = false,
    val phoneNumber: String = "",
    val otp: String = "",
    val isGoogleSignInInProgress: Boolean = false,
) {
    val canSignIn: Boolean get() = !isBusy && user == null
    val isOtpStep: Boolean get() = otpRequested && user == null
    val canEditPhoneNumber: Boolean get() = canSignIn
    val canEditOtp: Boolean get() = isOtpStep && !isBusy
    val canSendCode: Boolean get() = canSignIn && phoneNumber.isNotBlank()
    val canVerifyCode: Boolean get() = canSignIn && isOtpStep && codeSent && otp.length == 6
    val primaryActionEnabled: Boolean get() = if (isOtpStep) canVerifyCode else canSendCode
    val isPrimaryActionLoading: Boolean get() = isBusy && !isGoogleSignInInProgress
    val errorMessage: String get() = if (isError) message else ""
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val googleSignIn: GoogleSignIn,
    private val phoneSignIn: PhoneSignIn,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        LoginUiState(
            phoneNumber = savedStateHandle["phoneNumber"] ?: "",
            otp = savedStateHandle["otp"] ?: "",
        )
    )
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

    fun onPhoneNumberChange(input: String) {
        if (!_state.value.canEditPhoneNumber) return
        val phoneNumber = input.trimStart().removePrefix("+84").filter { it in '0'..'9' }
        if (phoneNumber == _state.value.phoneNumber) return

        phoneSignIn.resetVerification()
        savedStateHandle["phoneNumber"] = phoneNumber
        savedStateHandle["otp"] = ""
        _state.update {
            it.copy(phoneNumber = phoneNumber, otp = "", otpRequested = false, codeSent = false,
                message = "", isError = false)
        }
    }

    fun onOtpChange(input: String) {
        if (!_state.value.canEditOtp) return
        val otp = input.filter { it in '0'..'9' }.take(6)
        savedStateHandle["otp"] = otp
        _state.update { it.copy(otp = otp) }
    }

    fun onPrimaryAction(activity: Activity) {
        if (!_state.value.primaryActionEnabled) return
        if (_state.value.isOtpStep) verifyCode() else sendCode(activity)
    }

    fun signInWithGoogle(activity: Activity) {
        if (!_state.value.canSignIn) return
        _state.update {
            it.copy(isBusy = true, isGoogleSignInInProgress = true, otpRequested = false, codeSent = false,
                message = "Đang đăng nhập với Google...", isError = false)
        }
        viewModelScope.launch { showSignInResult(googleSignIn.signIn(activity)) }
    }

    fun sendCode(activity: Activity) {
        val currentState = _state.value
        if (!currentState.canSendCode) return
        savedStateHandle["otp"] = ""
        _state.update {
            it.copy(isBusy = true, isGoogleSignInInProgress = false, otp = "", otpRequested = true, codeSent = false,
                message = "Đang yêu cầu mã OTP từ Firebase. Bạn có thể nhập mã khi nhận được.", isError = false)
        }
        phoneSignIn.sendCode(
            activity = activity,
            phoneNumber = currentState.phoneNumber,
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

    fun verifyCode() {
        val currentState = _state.value
        if (!currentState.canVerifyCode) return
        _state.update {
            it.copy(isBusy = true, isGoogleSignInInProgress = false, message = "Đang xác nhận OTP...", isError = false)
        }
        viewModelScope.launch { showSignInResult(phoneSignIn.verifyCode(currentState.otp)) }
    }

    fun signOut() {
        if (_state.value.isBusy || _state.value.user == null) return
        _state.update {
            it.copy(isBusy = true, isGoogleSignInInProgress = false, message = "Đang đăng xuất...", isError = false)
        }
        viewModelScope.launch {
            val result = repository.signOut()
            savedStateHandle["otp"] = ""
            _state.update {
                it.copy(
                    isBusy = false,
                    otpRequested = false,
                    codeSent = false,
                    otp = "",
                    user = null,
                    message = if (result.isSuccess) "Đã đăng xuất."
                        else "Đã đăng xuất trên thiết bị; backend báo: ${result.exceptionOrNull()?.message}",
                    isError = result.isFailure,
                )
            }
        }
    }

    private fun showSignInResult(result: Result<CurrentUser>) {
        result.fold(
            onSuccess = { user ->
                _state.update {
                    it.copy(isBusy = false, isGoogleSignInInProgress = false, user = user,
                        message = "Firebase và backend đã đăng nhập thành công.", isError = false)
                }
            },
            onFailure = { showError(it) },
        )
    }

    private fun showError(error: Throwable) {
        _state.update {
            it.copy(isBusy = false, isGoogleSignInInProgress = false,
                message = error.message ?: "Đăng nhập thất bại.", isError = true)
        }
    }
}
