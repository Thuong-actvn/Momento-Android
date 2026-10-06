package com.example.momento.core.di

import com.example.momento.feature.auth.data.remote.AuthApi
import com.example.momento.feature.auth.data.repository.AuthRepositoryImpl
import com.example.momento.feature.auth.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    abstract fun authRepository(implementation: AuthRepositoryImpl): AuthRepository

    companion object {
        @Provides
        @Singleton
        fun authApi(retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
    }
}
