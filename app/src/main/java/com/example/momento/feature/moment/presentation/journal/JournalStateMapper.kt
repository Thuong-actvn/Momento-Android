package com.example.momento.feature.moment.presentation.journal

import com.example.momento.R
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.MomentOwner

/** Converts loaded photos and profiles to UI state without making API calls. */
object JournalStateMapper {
    fun photosLoaded(
        current: JournalUiState,
        result: JournalPhotoResult,
        load: JournalLoad,
        user: CurrentUser?,
    ): JournalUiState {
        val oldItems = if (load == JournalLoad.INITIAL) emptyList() else current.items
        val previousById = oldItems.associateBy { it.moment.id }
        val newItems = result.moments.map { moment ->
            val previous = previousById[moment.id]
            val preview = if (previous != null && previous.moment.imageUrl == moment.imageUrl) {
                previous.preview ?: previous.moment.preview
            } else {
                null
            }
            JournalItem(moment = moment, preview = preview)
        }
        val combined = if (load == JournalLoad.MORE) oldItems + newItems else newItems + oldItems
        val items = combined.distinctBy { it.moment.id }
        val error = loadError(result, load, items.isEmpty())
        val refreshFailed = load == JournalLoad.REFRESH &&
            (!result.hasSuccessfulPage || error == R.string.journal_network_error)
        val fullError = items.isEmpty() || refreshFailed

        return current.copy(
            items = withOwners(items, current.friends, user),
            isLoading = false,
            isRefreshing = false,
            isLoadingMore = false,
            hasMore = result.hasMore,
            error = if (fullError) error else null,
            loadMoreError = if (fullError) null else error,
            isOffline = result.isOffline,
        )
    }

    private fun loadError(result: JournalPhotoResult, load: JournalLoad, isEmpty: Boolean): Int? {
        if (result.hasInvalidCursor) return R.string.journal_pagination_error
        if (!result.isOffline) return result.errors.firstOrNull()?.let(::photoError)

        val otherError = result.errors.firstOrNull { error ->
            (error as? MomentException)?.reason != MomentError.NETWORK
        }
        if (otherError != null) return photoError(otherError)

        val userAskedForMore = load == JournalLoad.REFRESH || load == JournalLoad.MORE
        return if (isEmpty || (userAskedForMore && !result.addedCachedPhotos)) {
            R.string.journal_network_error
        } else {
            null
        }
    }

    fun friendsLoaded(
        current: JournalUiState,
        profiles: List<PublicProfile>,
        user: CurrentUser?,
    ): JournalUiState {
        val owners = profiles.distinctBy { it.id }.map { profile ->
            MomentOwner(
                id = profile.id,
                username = profile.username,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
            )
        }
        return current.copy(
            friends = owners,
            items = withOwners(current.items, owners, user),
            isLoadingFriends = false,
            friendsError = null,
        )
    }

    fun ownersUpdated(current: JournalUiState, user: CurrentUser?): JournalUiState {
        return current.copy(items = withOwners(current.items, current.friends, user))
    }

    private fun withOwners(
        items: List<JournalItem>,
        friends: List<MomentOwner>,
        user: CurrentUser?,
    ): List<JournalItem> {
        val owners = friends.associateBy { it.id }.toMutableMap()
        if (user != null) {
            owners[user.id] = MomentOwner(
                id = user.id,
                username = user.username,
                displayName = user.displayName,
                avatarUrl = user.avatarUrl,
            )
        }
        return items.map { item ->
            item.copy(
                owner = owners[item.moment.ownerId],
                isOwn = item.moment.ownerId == user?.id,
            )
        }
    }

    fun photoError(error: Throwable): Int {
        return when ((error as? MomentException)?.reason) {
            MomentError.NETWORK -> R.string.journal_network_error
            MomentError.SERVER -> R.string.journal_server_error
            else -> R.string.journal_error
        }
    }
}
