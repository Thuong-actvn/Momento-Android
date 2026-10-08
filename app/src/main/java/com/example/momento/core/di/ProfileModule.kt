package com.example.momento.core.di

import com.example.momento.feature.profile.data.remote.ProfileApi
import com.example.momento.feature.profile.data.repository.ProfileRepositoryImpl
import com.example.momento.feature.profile.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    abstract fun profileRepository(implementation: ProfileRepositoryImpl): ProfileRepository

    companion object {
        @Provides
        @Singleton
        fun profileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)
    }
}
