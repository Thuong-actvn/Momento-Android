package com.example.momento.core.di

import com.example.momento.feature.friend.data.remote.FriendApi
import com.example.momento.feature.friend.data.repository.FriendRepositoryImpl
import com.example.momento.feature.friend.domain.repository.FriendRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class FriendModule {
    @Binds
    abstract fun friendRepository(implementation: FriendRepositoryImpl): FriendRepository

    companion object {
        @Provides
        @Singleton
        fun friendApi(retrofit: Retrofit): FriendApi = retrofit.create(FriendApi::class.java)
    }
}
