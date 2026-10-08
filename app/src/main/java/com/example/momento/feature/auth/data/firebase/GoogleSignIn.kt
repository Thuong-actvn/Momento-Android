package com.example.momento.feature.auth.data.firebase

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.example.momento.R
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.auth.domain.repository.AuthProvider
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.auth.domain.repository.AuthRepository
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.Lazy
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

class GoogleSignIn @Inject constructor(
    private val firebase: Lazy<FirebaseAuth>,
    private val repository: AuthRepository,
) {
    suspend fun signIn(activity: Activity): Result<CurrentUser> = suspendRunCatching {
        val clientId = activity.getString(R.string.default_web_client_id)
        val option = GetSignInWithGoogleOption.Builder(clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val credential = CredentialManager.create(activity).getCredential(activity, request).credential
        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unsupported Google credential"
        }
        val googleToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        firebase.get().signInWithCredential(GoogleAuthProvider.getCredential(googleToken, null)).await()
        repository.syncUser(AuthProvider.GOOGLE).getOrThrow()
    }
}
