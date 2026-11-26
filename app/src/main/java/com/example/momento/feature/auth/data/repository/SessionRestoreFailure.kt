package com.example.momento.feature.auth.data.repository

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

internal enum class SessionRestoreFailure {
    Network, Denied, Other,
}

internal fun classifySessionRestoreFailure(error: Throwable): SessionRestoreFailure {
    var cause: Throwable? = error
    while (cause != null) {
        when (cause) {
            is HttpException -> return classifyApiFailure(cause.toApiException())
            is ApiException -> return classifyApiFailure(cause)
            is FirebaseAuthInvalidUserException -> return SessionRestoreFailure.Denied
            is FirebaseNetworkException, is UnknownHostException,
            is SocketTimeoutException, is SocketException -> return SessionRestoreFailure.Network
        }
        cause = cause.cause
    }
    return SessionRestoreFailure.Other
}

private fun classifyApiFailure(error: ApiException): SessionRestoreFailure {
    if (error.statusCode == 401 || error.statusCode == 403) {
        return SessionRestoreFailure.Denied
    }
    return when (error.code) {
        "UNAUTHORIZED", "FORBIDDEN", "USER_NOT_FOUND",
        "FIREBASE_TOKEN_INVALID", "FIREBASE_TOKEN_EXPIRED" -> SessionRestoreFailure.Denied
        else -> SessionRestoreFailure.Other
    }
}
