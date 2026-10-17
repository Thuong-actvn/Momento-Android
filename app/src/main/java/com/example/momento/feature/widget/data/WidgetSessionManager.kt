package com.example.momento.feature.widget.data

import com.example.momento.core.network.ApiException
import com.example.momento.core.network.toApiException
import com.example.momento.feature.auth.data.remote.AuthApi
import com.example.momento.feature.widget.data.local.WidgetAccount
import com.example.momento.feature.widget.data.local.WidgetLocalStore
import com.example.momento.feature.widget.data.local.WidgetSessionChangedException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class WidgetSessionManager(
    private val localStore: WidgetLocalStore,
    private val authApi: AuthApi,
    private val currentFirebaseUid: () -> String?,
) {
    suspend fun current(): WidgetAccount? {
        val account = localStore.read().account ?: return null
        if (!matchesIdentity(account)) {
            return null
        }
        return account
    }

    fun matchesIdentity(account: WidgetAccount): Boolean {
        return currentFirebaseUid() == account.firebaseUid
    }

    fun checkIdentity(account: WidgetAccount) {
        if (!matchesIdentity(account)) {
            throw WidgetSessionChangedException()
        }
    }

    suspend fun checkCurrent(account: WidgetAccount) {
        checkIdentity(account)
        if (localStore.read().account != account) {
            throw WidgetSessionChangedException()
        }
    }

    /** Verify the account once, then run the job without changing the foreground user state. */
    suspend fun <T> withAccount(block: suspend (WidgetAccount) -> T): T {
        val account = current() ?: throw WidgetSessionChangedException()
        try {
            val profile = authApi.me().requireData()
            if (profile.id != account.backendUserId || !profile.isProfileCompleted) {
                localStore.clearSession(account)
                throw WidgetSessionChangedException()
            }

            checkCurrent(account)
            val result = block(account)
            checkCurrent(account)
            return result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkCurrent(account)
            val failure = when (error) {
                is HttpException -> error.toApiException()
                else -> error
            }
            if (failure is ApiException) {
                val accountUnavailable = failure.statusCode == 401 ||
                    failure.statusCode == 403 || failure.code == "USER_NOT_FOUND"
                if (accountUnavailable) {
                    localStore.clearSession(account)
                }
            }
            throw failure
        }
    }
}
