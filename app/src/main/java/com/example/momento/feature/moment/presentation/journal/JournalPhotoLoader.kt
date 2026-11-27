package com.example.momento.feature.moment.presentation.journal

import com.example.momento.feature.moment.data.realtime.loadMomentUpdates
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.repository.MomentRepository
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Loads Journal photos and keeps a separate cursor for each API endpoint. */
class JournalPhotoLoader(private val moments: MomentRepository) {
    private var pages = emptyMap<JournalSource, JournalPage>()
    private val pendingPhotos = mutableMapOf<String, Moment>()

    fun reset() {
        pages = emptyMap()
        pendingPhotos.clear()
    }

    suspend fun load(filter: JournalFilter, load: JournalLoad): JournalPhotoResult {
        val requestPages = if (load == JournalLoad.INITIAL || pages.isEmpty()) newPages(filter) else pages
        val refreshHead = load == JournalLoad.REFRESH || load == JournalLoad.SYNC
        val pendingPages = if (load == JournalLoad.MORE) {
            requestPages.filterValues { it.hasMore }
        } else {
            requestPages
        }

        // All requests both endpoints concurrently, without mixing their cursors.
        val results = coroutineScope {
            pendingPages.map { (source, page) ->
                async {
                    val result = if (refreshHead) {
                        loadMomentUpdates(page.latestMomentId, page.latestMomentAt) { cursor ->
                            requestPage(source, filter, cursor)
                        }
                    } else {
                        requestPage(source, filter, page.cursor)
                    }
                    JournalPageResult(source = source, result = result)
                }
            }.awaitAll()
        }
        currentCoroutineContext().ensureActive()

        val errors = results.mapNotNull { it.result.exceptionOrNull() }
        for (error in errors) {
            if (error is CancellationException) throw error
        }

        val successfulPages = results.filter { it.result.isSuccess }
        var hasInvalidCursor = false
        val isOffline = successfulPages.any { it.result.getOrThrow().fromCache } ||
            errors.any { (it as? MomentException)?.reason == MomentError.NETWORK }

        for (pageResult in successfulPages) {
            val page = pageResult.result.getOrThrow()
            for (photo in page.data) {
                pendingPhotos[photo.id] = photo
            }
            if (page.fromCache) continue

            val tracker = requestPages.getValue(pageResult.source)
            if (refreshHead && tracker.latestMomentId != null) {
                // Refresh the head of each source while keeping its cursor for older photos.
                tracker.updateHead(page.data)
            } else if (!tracker.accept(page)) {
                hasInvalidCursor = true
            }
        }

        if (successfulPages.isNotEmpty()) {
            pages = requestPages
        }

        // Wait for both sources to cover a date before showing older groups.
        val visibleSince = if (filter == JournalFilter.All && !isOffline) {
            pages.values.filter { it.hasMore }.mapNotNull { it.oldestLoadedAt }.maxOrNull()
        } else {
            null
        }
        val visiblePhotos = mutableListOf<Moment>()
        val waiting = pendingPhotos.values.iterator()
        while (waiting.hasNext()) {
            val photo = waiting.next()
            val createdAt = photo.createdAt
            if (visibleSince == null || (createdAt != null && createdAt >= visibleSince)) {
                visiblePhotos.add(photo)
                waiting.remove()
            }
        }

        return JournalPhotoResult(
            moments = visiblePhotos,
            errors = errors,
            hasSuccessfulPage = successfulPages.isNotEmpty(),
            hasMore = pages.values.any { it.hasMore },
            hasInvalidCursor = hasInvalidCursor,
            isOffline = isOffline,
        )
    }

    private suspend fun requestPage(
        source: JournalSource,
        filter: JournalFilter,
        cursor: String?,
    ): Result<CursorPage<Moment>> {
        return when (source) {
            JournalSource.MY_PHOTOS -> moments.getMyMoments(cursor = cursor)
            JournalSource.FRIENDS_FEED -> moments.getFeed(cursor = cursor)
            JournalSource.ONE_FRIEND -> moments.getFriendFeed(
                friendId = (filter as JournalFilter.Friend).id,
                cursor = cursor,
            )
        }
    }

    private fun newPages(filter: JournalFilter): Map<JournalSource, JournalPage> {
        val sources = when (filter) {
            JournalFilter.All -> listOf(JournalSource.MY_PHOTOS, JournalSource.FRIENDS_FEED)
            JournalFilter.OnlyMe -> listOf(JournalSource.MY_PHOTOS)
            is JournalFilter.Friend -> listOf(JournalSource.ONE_FRIEND)
        }
        return sources.associateWith { JournalPage() }
    }
}

data class JournalPhotoResult(
    val moments: List<Moment>,
    val errors: List<Throwable>,
    val hasSuccessfulPage: Boolean,
    val hasMore: Boolean,
    val hasInvalidCursor: Boolean,
    val isOffline: Boolean = false,
    val addedCachedPhotos: Boolean = false,
)

private enum class JournalSource {
    MY_PHOTOS,
    FRIENDS_FEED,
    ONE_FRIEND,
}

private data class JournalPageResult(
    val source: JournalSource,
    val result: Result<CursorPage<Moment>>,
)

private class JournalPage {
    var latestMomentId: String? = null
        private set
    var latestMomentAt: Instant? = null
        private set
    var oldestLoadedAt: Instant? = null
        private set
    var cursor: String? = null
    var hasMore: Boolean = true
    private val usedCursors = mutableSetOf<String>()

    fun updateHead(photos: List<Moment>) {
        val newest = photos.firstOrNull() ?: return
        latestMomentId = newest.id
        latestMomentAt = newest.createdAt
    }

    fun accept(page: CursorPage<Moment>): Boolean {
        val invalidCursor = page.nextCursor.isNullOrBlank() ||
            page.nextCursor == cursor ||
            page.nextCursor in usedCursors ||
            page.data.isEmpty()
        if (page.hasMore && invalidCursor) {
            return false
        }

        if (latestMomentId == null) updateHead(page.data)
        val oldestOnPage = page.data.mapNotNull { it.createdAt }.minOrNull()
        val previousOldest = oldestLoadedAt
        if (oldestOnPage != null && (previousOldest == null || oldestOnPage < previousOldest)) {
            oldestLoadedAt = oldestOnPage
        }
        cursor?.let { usedCursors.add(it) }
        cursor = page.nextCursor
        hasMore = page.hasMore
        return true
    }
}
