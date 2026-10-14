package com.example.momento.core.di

import com.example.momento.feature.chat.data.remote.ChatApi
import com.example.momento.feature.chat.data.repository.ChatRepositoryImpl
import com.example.momento.feature.chat.domain.repository.ChatRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatModule {
    @Binds
    abstract fun chatRepository(implementation: ChatRepositoryImpl): ChatRepository

    companion object {
        @Provides
        @Singleton
        fun chatApi(retrofit: Retrofit): ChatApi = retrofit.create(ChatApi::class.java)

        @Provides
        @Singleton
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
