package com.example.momento.feature.moment.data.realtime

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import java.time.Instant
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Read new pages until they reach the newest photo already loaded by this screen. */
suspend fun loadMomentUpdates(
    latestMomentId: String?,
    latestMomentAt: Instant? = null,
    loadPage: suspend (String?) -> Result<CursorPage<Moment>>,
): Result<CursorPage<Moment>> = suspendRunCatching {
    val moments = mutableListOf<Moment>()
    val visitedCursors = mutableSetOf<String>()
    var cursor: String? = null
    var page = loadPage(cursor).getOrThrow()

    while (true) {
        currentCoroutineContext().ensureActive()
        if (page.hasMore) {
            val next = page.nextCursor
            if (next.isNullOrBlank() || next == cursor || next in visitedCursors || page.data.isEmpty()) {
                throw MomentException(MomentError.INVALID_RESPONSE)
            }
        }
        moments.addAll(page.data)
        val reachedHistory = page.data.any { photo ->
            val createdAt = photo.createdAt
            photo.id == latestMomentId ||
                (createdAt != null && latestMomentAt != null && createdAt < latestMomentAt)
        }
        if (latestMomentId == null || reachedHistory || !page.hasMore) {
            break
        }
        cursor = checkNotNull(page.nextCursor)
        visitedCursors.add(cursor)
        page = loadPage(cursor).getOrThrow()
    }

    CursorPage(moments.distinctBy { it.id }, page.nextCursor, page.hasMore, fromCache = page.fromCache)
}
