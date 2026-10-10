package com.example.momento.core.di

import com.example.momento.feature.moment.data.remote.MomentApi
import com.example.momento.feature.moment.data.repository.MomentRepositoryImpl
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class MomentModule {
    @Binds
    abstract fun momentRepository(implementation: MomentRepositoryImpl): MomentRepository

    companion object {
        @Provides
        @Singleton
        fun momentApi(retrofit: Retrofit): MomentApi = retrofit.create(MomentApi::class.java)
    }
}