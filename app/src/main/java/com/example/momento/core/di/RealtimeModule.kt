package com.example.momento.core.di

import com.example.momento.core.background.BackgroundWorkScheduler
import com.example.momento.core.realtime.AndroidRealtimeNetworkMonitor
import com.example.momento.core.realtime.RealtimeNetworkMonitor
import com.example.momento.core.realtime.RealtimeSocketClient
import com.example.momento.core.realtime.StompRealtimeSocketClient
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.data.realtime.MomentUpdates
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class RealtimeModule {
    @Binds
    abstract fun socket(implementation: StompRealtimeSocketClient): RealtimeSocketClient

    @Binds
    abstract fun network(implementation: AndroidRealtimeNetworkMonitor): RealtimeNetworkMonitor

    companion object {
        @Provides
        @Singleton
        fun momentUpdates(
            users: CurrentUserStore,
            scope: CoroutineScope,
            work: BackgroundWorkScheduler,
        ): MomentUpdates {
            return MomentUpdates(users, scope) {
                work.refreshWidgetData()
            }
        }

        @Provides
        @Singleton
        fun applicationScope(): CoroutineScope {
            return CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }
    }
}
