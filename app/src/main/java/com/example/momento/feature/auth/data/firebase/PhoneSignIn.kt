package com.example.momento.feature.auth.data.firebase

import android.app.Activity
import android.telephony.PhoneNumberUtils
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.auth.domain.model.AuthProvider
import com.example.momento.feature.auth.domain.model.AuthUser
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dagger.Lazy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/** Keep one instance in the Android auth host while sending and verifying OTP. */
class PhoneSignIn @Inject constructor(
    private val firebase: Lazy<FirebaseAuth>,
    private val repository: AuthRepository,
) {
    private var verificationId: String? = null

    fun sendCode(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: () -> Unit,
        onAutoVerified: (PhoneAuthCredential) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        verificationId = null
        try {
            val formattedPhone = requireNotNull(
                PhoneNumberUtils.formatNumberToE164(phoneNumber.trim(), "VN")
            ) {
                "Số điện thoại không hợp lệ. Ví dụ: 0901234567 hoặc +84901234567"
            }
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) {
                    verificationId = id
                    onCodeSent()
                }

                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    onAutoVerified(credential)
                }

                override fun onVerificationFailed(error: FirebaseException) {
                    onError(error)
                }
            }
            val options = PhoneAuthOptions.newBuilder(firebase.get())
                .setActivity(activity)
                .setPhoneNumber(formattedPhone)
                .setTimeout(60, TimeUnit.SECONDS)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            onError(e)
        }
    }

    suspend fun verifyCode(code: String): Result<AuthUser> = suspendRunCatching {
        val id = checkNotNull(verificationId) { "Send an OTP first" }
        require(code.matches(Regex("^[0-9]{6}$"))) { "OTP must contain 6 digits" }
        signInWithCredential(PhoneAuthProvider.getCredential(id, code)).getOrThrow()
    }

    /** Also used when Firebase verifies the phone automatically. */
    suspend fun signInWithCredential(credential: PhoneAuthCredential): Result<AuthUser> = suspendRunCatching {
        firebase.get().signInWithCredential(credential).await()
        repository.syncUser(AuthProvider.PHONE).getOrThrow()
    }
}
