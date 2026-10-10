package com.example.momento.core.di

import com.example.momento.feature.moment.data.remote.MomentApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object MomentModule {
    @Provides
    @Singleton
    fun momentApi(retrofit: Retrofit): MomentApi = retrofit.create(MomentApi::class.java)
}