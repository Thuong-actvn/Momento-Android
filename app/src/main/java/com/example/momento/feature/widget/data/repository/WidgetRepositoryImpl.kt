package com.example.momento.feature.widget.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.friend.data.remote.FriendApi
import com.example.momento.feature.widget.data.WidgetSessionManager
import com.example.momento.feature.widget.data.local.WidgetLocalStore
import com.example.momento.feature.widget.data.remote.WidgetApi
import com.example.momento.feature.widget.data.remote.toWidgetSnapshot
import com.example.momento.feature.widget.domain.model.WidgetSnapshot
import com.example.momento.feature.widget.domain.repository.WidgetRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class WidgetRepositoryImpl @Inject constructor(
    private val widgetApi: WidgetApi,
    private val friendApi: FriendApi,
    private val localStore: WidgetLocalStore,
    private val sessionManager: WidgetSessionManager,
) : WidgetRepository {
    private val refreshMutex = Mutex()

    override val snapshot: Flow<WidgetSnapshot?> = localStore.data.map { state ->
        val account = state.account ?: return@map null
        if (!sessionManager.matchesIdentity(account)) {
            return@map null
        }

        state.snapshot
    }

    override suspend fun refresh(): Result<WidgetSnapshot> = suspendRunCatching {
        refreshMutex.withLock {
            sessionManager.withAccount { account ->
                val widgetItems = widgetApi.getPhotos()
                val friends = friendApi.getFriends().requireData()
                val snapshot = toWidgetSnapshot(
                    items = widgetItems,
                    friends = friends,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                )

                localStore.saveSnapshot(account, snapshot) {
                    sessionManager.checkIdentity(account)
                }
                snapshot
            }
        }
    }
}
