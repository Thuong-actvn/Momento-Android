package com.example.momento.core.di

import android.content.Context
import androidx.room.Room
import com.example.momento.core.database.MIGRATION_1_2
import com.example.momento.core.database.MomentoDatabase
import com.example.momento.feature.chat.data.local.ChatDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun momentoDatabase(@ApplicationContext context: Context): MomentoDatabase {
        return Room.databaseBuilder(context, MomentoDatabase::class.java, "momento.db")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    @Provides
    fun chatDao(database: MomentoDatabase): ChatDao = database.chatDao()
}
