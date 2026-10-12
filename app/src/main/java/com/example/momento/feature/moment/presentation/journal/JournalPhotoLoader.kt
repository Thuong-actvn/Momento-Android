package com.example.momento.feature.moment.presentation.journal

import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.repository.MomentRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Loads Journal photos and keeps a separate cursor for each API endpoint. */
class JournalPhotoLoader(private val moments: MomentRepository) {
    private var pages = emptyMap<JournalSource, JournalPage>()

    val hasMore: Boolean
        get() = pages.values.any { it.hasMore }

    fun reset() {
        pages = emptyMap()
    }

    suspend fun load(filter: JournalFilter, loadMore: Boolean): JournalPhotoResult {
        val requestPages = if (loadMore) pages else newPages(filter)
        val pendingPages = requestPages.filterValues { it.hasMore }

        // All requests both endpoints concurrently, without mixing their cursors.
        val results = coroutineScope {
            pendingPages.map { (source, page) ->
                async {
                    val result = requestPage(source, filter, page.cursor)
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
        val newMoments = mutableListOf<Moment>()
        var hasInvalidCursor = false

        for (pageResult in successfulPages) {
            val page = pageResult.result.getOrThrow()
            newMoments.addAll(page.data)

            val tracker = requestPages.getValue(pageResult.source)
            if (!tracker.accept(page)) {
                hasInvalidCursor = true
            }
        }

        // A fully failed refresh must leave the previous cursors available for retry.
        if (successfulPages.isNotEmpty()) {
            pages = requestPages
        }

        return JournalPhotoResult(
            moments = newMoments,
            errors = errors,
            hasSuccessfulPage = successfulPages.isNotEmpty(),
            hasMore = hasMore,
            hasInvalidCursor = hasInvalidCursor,
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
    var cursor: String? = null
    var hasMore: Boolean = true
    private val usedCursors = mutableSetOf<String>()

    fun accept(page: CursorPage<Moment>): Boolean {
        val invalidCursor = page.nextCursor.isNullOrBlank() ||
            page.nextCursor == cursor ||
            page.nextCursor in usedCursors ||
            page.data.isEmpty()
        if (page.hasMore && invalidCursor) {
            return false
        }

        cursor?.let { usedCursors.add(it) }
        cursor = page.nextCursor
        hasMore = page.hasMore
        return true
    }
}
