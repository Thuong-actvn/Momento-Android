package com.example.momento.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.Invocation

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class NoAuth

class AuthInterceptor(
    private val getIdToken: () -> String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.tag(Invocation::class.java)?.method()
        if (method?.isAnnotationPresent(NoAuth::class.java) == true) {
            return chain.proceed(request)
        }

        val token = try {
            getIdToken()
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Firebase token retrieval was interrupted", error)
        } catch (error: Exception) {
            throw IOException(error.message ?: "Firebase ID token is unavailable", error)
        }
        if (token.isBlank()) throw IOException("Firebase ID token is missing")

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        )
    }
}
