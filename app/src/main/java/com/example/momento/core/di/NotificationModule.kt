package com.example.momento.core.di

import com.example.momento.feature.notification.data.remote.NotificationApi
import com.example.momento.feature.notification.data.repository.NotificationRepositoryImpl
import com.example.momento.feature.notification.domain.repository.NotificationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {
    @Binds
    abstract fun notificationRepository(implementation: NotificationRepositoryImpl): NotificationRepository

    companion object {
        @Provides
        @Singleton
        fun notificationApi(retrofit: Retrofit): NotificationApi =
            retrofit.create(NotificationApi::class.java)
    }
}
