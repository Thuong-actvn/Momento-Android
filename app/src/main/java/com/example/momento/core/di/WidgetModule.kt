package com.example.momento.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.work.WorkManager
import com.example.momento.core.messaging.DeviceTokenApi
import com.example.momento.core.messaging.FcmTokenManager
import com.example.momento.feature.auth.data.remote.AuthApi
import com.example.momento.feature.widget.data.WidgetSessionManager
import com.example.momento.feature.widget.data.local.WidgetLocalStore
import com.example.momento.feature.widget.data.remote.WidgetApi
import com.example.momento.feature.widget.data.repository.WidgetRepositoryImpl
import com.example.momento.feature.widget.domain.repository.WidgetRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import dagger.Binds
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import okio.Path.Companion.toOkioPath
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    abstract fun widgetRepository(implementation: WidgetRepositoryImpl): WidgetRepository

    companion object {
        @Provides
        @Singleton
        fun widgetSessionManager(
            localStore: WidgetLocalStore,
            authApi: AuthApi,
            auth: Lazy<FirebaseAuth>,
        ): WidgetSessionManager {
            return WidgetSessionManager(localStore, authApi) {
                auth.get().currentUser?.uid
            }
        }

        @Suppress("DEPRECATION")
        @Provides
        @Singleton
        fun fcmTokenManager(
            api: DeviceTokenApi,
            sessions: WidgetSessionManager,
            messaging: Lazy<FirebaseMessaging>,
        ): FcmTokenManager {
            return FcmTokenManager(api, sessions) {
                messaging.get().token.await()
            }
        }

        @Provides
        @Singleton
        fun widgetApi(retrofit: Retrofit): WidgetApi = retrofit.create(WidgetApi::class.java)

        @Provides
        @Singleton
        fun tokenApi(retrofit: Retrofit): DeviceTokenApi = retrofit.create(DeviceTokenApi::class.java)

        @Provides
        @Singleton
        fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)

        @Provides
        @Singleton
        fun widgetPreferences(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile("momento_widget").toOkioPath() },
            )
    }
}
