package com.example.momento.feature.widget.domain.repository

import com.example.momento.feature.widget.domain.model.WidgetData
import com.example.momento.feature.widget.domain.model.WidgetSnapshot
import kotlinx.coroutines.flow.Flow

interface WidgetRepository {
    /** Snapshot and photo source are read together from the same saved state. */
    val data: Flow<WidgetData>

    /** A successful refresh replaces the snapshot; network failures preserve the last one. */
    suspend fun refresh(): Result<WidgetSnapshot>

    suspend fun selectFriend(friendId: String?): Result<Unit>
}
