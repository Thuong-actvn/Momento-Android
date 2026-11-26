package com.example.momento.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoAuth

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SessionCheck

class OfflineSessionException : IOException("The cached session is waiting for a connection")

class AuthTokenUnavailableException(cause: Throwable? = null) :
    IOException(cause?.message ?: "Firebase ID token is unavailable", cause)

class AuthInterceptor(
    private val isUsingCachedSession: () -> Boolean = { false },
    private val getIdToken: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.tag(Invocation::class.java)?.method()
        if (method?.isAnnotationPresent(NoAuth::class.java) == true) {
            return chain.proceed(request)
        }
        if (isUsingCachedSession() && method?.isAnnotationPresent(SessionCheck::class.java) != true) {
            throw OfflineSessionException()
        }

        val token = try {
            getIdToken()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw AuthTokenUnavailableException(error)
        } catch (error: Exception) {
            throw AuthTokenUnavailableException(error)
        }
        if (token.isBlank()) throw AuthTokenUnavailableException()

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
