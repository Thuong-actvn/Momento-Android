package com.example.momento.core.di

import com.example.momento.BuildConfig
import com.example.momento.core.network.AuthInterceptor
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun authInterceptor(auth: Lazy<FirebaseAuth>): AuthInterceptor = AuthInterceptor {
        val user = checkNotNull(auth.get().currentUser) { "Sign in first" }
        checkNotNull(Tasks.await(user.getIdToken(false), 30, TimeUnit.SECONDS).token) {
            "Firebase ID token is missing"
        }
    }

    @Provides
    @Singleton
    fun retrofit(authInterceptor: AuthInterceptor): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BACKEND_BASE_URL)
        .client(OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
