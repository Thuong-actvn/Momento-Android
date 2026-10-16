package com.example.momento.feature.widget.domain.repository

import com.example.momento.feature.widget.domain.model.WidgetSnapshot
import kotlinx.coroutines.flow.Flow

interface WidgetRepository {
    val snapshot: Flow<WidgetSnapshot?>

    /** A successful refresh replaces the snapshot; network failures preserve the last one. */
    suspend fun refresh(): Result<WidgetSnapshot>
}
