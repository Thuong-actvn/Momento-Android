package com.example.momento.feature.moment.presentation.journal

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.R
import com.example.momento.core.realtime.RealtimeNetworkMonitor
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.friend.data.remote.FriendError
import com.example.momento.feature.friend.data.remote.FriendException
import com.example.momento.feature.friend.domain.repository.FriendRepository
import com.example.momento.feature.moment.data.realtime.MomentUpdates
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val moments: MomentRepository,
    private val friends: FriendRepository,
    private val userStore: CurrentUserStore,
    private val savedState: SavedStateHandle,
    private val updates: MomentUpdates,
    private val network: RealtimeNetworkMonitor,
) : ViewModel() {
    private val _state = MutableStateFlow(JournalUiState(isLoading = true))
    val state = _state.asStateFlow()

    private var photosJob: Job? = null
    private var friendsJob: Job? = null
    private var cachedPhotosJob: Job? = null
    private var ownerId: String? = null
    private val photoLoader = JournalPhotoLoader(moments)
    private val deletedMomentIds = mutableSetOf<String>()
    private var momentVersion = updates.version.value
    private var realtimeRefreshRequested = false
    private var isVisible = false
    private val networkWatch = network.watch {
        viewModelScope.launch { refreshWhenReady() }
    }

    init {
        viewModelScope.launch {
            userStore.currentUser.collect { user ->
                if (user == null) {
                    clearSession()
                } else if (user.id != ownerId) {
                    realtimeRefreshRequested = false
                    photosJob?.cancel()
                    friendsJob?.cancel()
                    cachedPhotosJob?.cancel()
                    photoLoader.reset()
                    deletedMomentIds.clear()
                    ownerId = user.id
                    val sameUser = savedState.get<String>("journal_owner") == user.id
                    _state.value = JournalUiState(
                        filter = if (sameUser) restoredFilter() else JournalFilter.All,
                    )
                    saveFilter()
                    loadPhotos(JournalLoad.INITIAL)
                    loadFriends()
                } else {
                    _state.update { current ->
                        JournalStateMapper.ownersUpdated(current = current, user = user)
                    }
                }
            }
        }
        viewModelScope.launch {
            userStore.completedProfileSession.collect {
                if (_state.value.isOffline) refreshWhenReady()
            }
        }
        viewModelScope.launch {
            updates.version.collectLatest { version ->
                if (version != momentVersion) {
                    momentVersion = version
                    delay(500)
                    realtimeRefreshRequested = true
                    refreshIfRequested()
                }
            }
        }
    }

    fun setVisible(visible: Boolean) {
        isVisible = visible
        if (visible && _state.value.isOffline) refreshWhenReady()
    }

    private fun refreshWhenReady() {
        if (!isVisible || !canSyncPhotos()) return
        if (ownerId != userStore.currentUser.value?.id) return
        if (photosJob?.isActive == true) {
            realtimeRefreshRequested = true
            return
        }
        refresh()
    }

    private fun canSyncPhotos(): Boolean {
        return userStore.completedProfileSession.value != null && network.hasNetwork()
    }

    private fun refreshIfRequested() {
        if (!realtimeRefreshRequested) return
        if (ownerId == null || userStore.currentUser.value?.id != ownerId) return
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        realtimeRefreshRequested = false
        loadPhotos(JournalLoad.SYNC)
        loadFriends()
    }

    fun selectFilter(filter: JournalFilter) {
        val current = _state.value
        if (filter == current.filter) return
        if (filter is JournalFilter.Friend && current.friends.none { it.id == filter.id }) return

        _state.update { it.copy(filter = filter, isOffline = false) }
        saveFilter()
        loadPhotos(JournalLoad.INITIAL)
        if (current.filter == JournalFilter.OnlyMe &&
            filter != JournalFilter.OnlyMe && _state.value.friends.isEmpty()
        ) {
            loadFriends()
        }
    }

    fun retry() {
        val current = _state.value
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        when {
            current.loadMoreError != null -> loadPhotos(JournalLoad.MORE)
            current.items.isEmpty() -> loadPhotos(JournalLoad.INITIAL)
            else -> refresh()
        }
    }

    fun refresh() {
        if (_state.value.isLoading || _state.value.isRefreshing) return
        loadPhotos(JournalLoad.REFRESH)
        loadFriends()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.loadMoreError != null || current.error != null) return
        if (current.isLoading || current.isRefreshing || current.isLoadingMore) return
        loadPhotos(JournalLoad.MORE)
    }

    fun retryFriends() {
        if (!_state.value.isLoadingFriends) loadFriends()
    }

    fun removeMoment(momentId: String) {
        deletedMomentIds.add(momentId)
        _state.update { current ->
            current.copy(items = current.items.filterNot { it.moment.id == momentId })
        }
    }

    fun savePreview(item: JournalItem, bitmap: Bitmap) {
        val version = userStore.sessionVersion.value
        val photo = item.moment
        viewModelScope.launch {
            if (userStore.sessionVersion.value != version) return@launch
            val preview = moments.saveMomentPreview(photo, bitmap) ?: return@launch
            if (userStore.sessionVersion.value != version) return@launch
            _state.update { current ->
                current.copy(items = current.items.map { row ->
                    if (row.moment.id == photo.id && row.moment.imageUrl == photo.imageUrl) {
                        row.copy(preview = preview)
                    } else {
                        row
                    }
                })
            }
        }
    }

    private fun loadPhotos(load: JournalLoad) {
        val session = userStore.sessionOrNull()
        if (session == null) {
            clearSession()
            return
        }

        photosJob?.cancel()
        val current = _state.value
        val filter = current.filter
        val visibleIds = current.items.map { it.moment.id }.toSet()
        _state.update {
            it.copy(
                items = if (load == JournalLoad.INITIAL) emptyList() else it.items,
                isLoading = load == JournalLoad.INITIAL,
                isRefreshing = load == JournalLoad.REFRESH || load == JournalLoad.SYNC,
                isLoadingMore = load == JournalLoad.MORE,
                hasMore = if (load == JournalLoad.INITIAL) false else it.hasMore,
                error = null,
                loadMoreError = null,
            )
        }
        if (load == JournalLoad.INITIAL) {
            photoLoader.reset()
            cachedPhotosJob?.cancel()
            if (filter == JournalFilter.OnlyMe) observeCachedPhotos(session)
        }

        photosJob = viewModelScope.launch {
            when {
                !canSyncPhotos() -> showCachedPhotos(load, session, filter, visibleIds)
                filter == JournalFilter.OnlyMe -> loadMyPhotos(load, session, visibleIds)
                else -> loadOtherPhotos(load, session, filter, visibleIds)
            }
            if (!isViewingFilter(session, filter)) return@launch
            refreshIfRequested()
        }
    }

    private suspend fun loadOtherPhotos(
        load: JournalLoad,
        session: CurrentUserStore.Session,
        filter: JournalFilter,
        visibleIds: Set<String>,
    ) {
        val result = photoLoader.load(filter = filter, load = load)
        currentCoroutineContext().ensureActive()
        if (!isViewingFilter(session, filter)) return

        when {
            result.errors.any(::isSessionError) -> clearSession()
            filter is JournalFilter.Friend && result.errors.any(::isNotFriends) -> returnToAll(filter.id)
            result.isOffline -> showCachedPhotos(load, session, filter, visibleIds, result)
            else -> publishPhotos(load, result)
        }
    }

    private suspend fun showCachedPhotos(
        load: JournalLoad,
        session: CurrentUserStore.Session,
        filter: JournalFilter,
        visibleIds: Set<String>,
        remoteResult: JournalPhotoResult? = null,
    ) {
        val ownerId = when (filter) {
            JournalFilter.All -> null
            JournalFilter.OnlyMe -> session.userId
            is JournalFilter.Friend -> filter.id
        }
        val result = moments.getCachedJournalPhotos(ownerId)
        currentCoroutineContext().ensureActive()
        if (!isViewingFilter(session, filter)) return

        val error = result.exceptionOrNull()
        if (error != null && isSessionError(error)) {
            clearSession()
            return
        }
        val cachedPhotos = result.getOrDefault(emptyList()).filterNot { it.id in deletedMomentIds }
        val photos = remoteResult?.moments.orEmpty() + cachedPhotos
        val hasMore = if (load == JournalLoad.INITIAL) photos.isNotEmpty() else _state.value.hasMore
        publishPhotos(
            load = load,
            result = JournalPhotoResult(
                moments = photos,
                errors = remoteResult?.errors.orEmpty() + listOfNotNull(error),
                hasSuccessfulPage = remoteResult?.hasSuccessfulPage == true || cachedPhotos.isNotEmpty(),
                hasMore = hasMore,
                hasInvalidCursor = remoteResult?.hasInvalidCursor == true,
                isOffline = true,
                addedCachedPhotos = cachedPhotos.any { it.id !in visibleIds },
            ),
        )
    }

    private fun publishPhotos(load: JournalLoad, result: JournalPhotoResult) {
        _state.update { current ->
            JournalStateMapper.photosLoaded(
                current = current,
                // A response started before deletion can still contain the deleted photo.
                result = result.copy(moments = result.moments.filterNot { it.id in deletedMomentIds }),
                load = load,
                user = userStore.currentUser.value,
            )
        }
    }

    private fun observeCachedPhotos(session: CurrentUserStore.Session) {
        cachedPhotosJob = viewModelScope.launch {
            moments.myJournalPhotos.collect { photos ->
                if (!isViewingFilter(session, JournalFilter.OnlyMe)) return@collect
                val items = photos.filterNot { it.moment.id in deletedMomentIds }.map { photo ->
                    JournalItem(moment = photo.moment, isOwn = true, preview = photo.preview)
                }
                _state.update { current ->
                    val next = current.copy(
                        items = items,
                        isLoading = current.isLoading && items.isEmpty(),
                    )
                    JournalStateMapper.ownersUpdated(next, userStore.currentUser.value)
                }
            }
        }
    }

    private suspend fun loadMyPhotos(
        load: JournalLoad,
        session: CurrentUserStore.Session,
        visibleIds: Set<String>,
    ) {
        val more = load == JournalLoad.MORE
        val result = if (more) moments.loadMoreMyJournal() else moments.refreshMyJournal()
        currentCoroutineContext().ensureActive()
        if (!isViewingFilter(session, JournalFilter.OnlyMe)) return

        val error = result.exceptionOrNull()
        if (error != null && isSessionError(error)) {
            clearSession()
            return
        }
        if ((error as? MomentException)?.reason == MomentError.NETWORK) {
            showCachedPhotos(load, session, JournalFilter.OnlyMe, visibleIds)
            return
        }
        val message = error?.let(JournalStateMapper::photoError)
        _state.update { current ->
            current.copy(
                isLoading = false,
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = result.getOrNull() ?: current.hasMore,
                error = if (more) null else message,
                loadMoreError = if (more) message else null,
                isOffline = false,
            )
        }
    }

    private fun isViewingFilter(session: CurrentUserStore.Session, filter: JournalFilter): Boolean {
        return _state.value.filter == filter &&
            userStore.sessionVersion.value == session.generation
    }

    private fun loadFriends() {
        if (_state.value.filter == JournalFilter.OnlyMe && !canSyncPhotos()) return
        val userId = userStore.currentUser.value?.id ?: return
        friendsJob?.cancel()
        _state.update { it.copy(isLoadingFriends = true) }
        friendsJob = viewModelScope.launch {
            val result = friends.getFriends()
            currentCoroutineContext().ensureActive()
            if (userStore.currentUser.value?.id != userId) return@launch

            result.onSuccess { profiles ->
                showFriends(profiles)
            }.onFailure { error ->
                if (error is CancellationException) throw error
                val reason = (error as? FriendException)?.reason
                if (reason == FriendError.UNAUTHENTICATED || reason == FriendError.SESSION_CHANGED) {
                    clearSession()
                } else {
                    _state.update {
                        it.copy(isLoadingFriends = false, friendsError = R.string.journal_friends_error)
                    }
                }
            }
        }
    }

    private fun showFriends(profiles: List<PublicProfile>) {
        _state.update { current ->
            JournalStateMapper.friendsLoaded(
                current = current,
                profiles = profiles,
                user = userStore.currentUser.value,
            )
        }
        val filter = _state.value.filter
        if (filter is JournalFilter.Friend && _state.value.friends.none { it.id == filter.id }) {
            returnToAll(filter.id)
        }
    }

    private fun returnToAll(friendId: String) {
        _state.update { it.copy(friends = it.friends.filterNot { owner -> owner.id == friendId }) }
        selectFilter(JournalFilter.All)
        loadFriends()
    }

    private fun isSessionError(error: Throwable): Boolean {
        val reason = (error as? MomentException)?.reason
        return reason == MomentError.UNAUTHENTICATED || reason == MomentError.SESSION_CHANGED
    }

    private fun isNotFriends(error: Throwable): Boolean {
        return (error as? MomentException)?.backendCode == "NOT_FRIENDS"
    }

    private fun restoredFilter(): JournalFilter {
        return when (val value = savedState.get<String>("journal_filter")) {
            null, "all" -> JournalFilter.All
            "me" -> JournalFilter.OnlyMe
            else -> JournalFilter.Friend(value)
        }
    }

    private fun saveFilter() {
        savedState["journal_owner"] = ownerId
        savedState["journal_filter"] = when (val filter = _state.value.filter) {
            JournalFilter.All -> "all"
            JournalFilter.OnlyMe -> "me"
            is JournalFilter.Friend -> filter.id
        }
    }

    private fun clearSession() {
        realtimeRefreshRequested = false
        photosJob?.cancel()
        friendsJob?.cancel()
        cachedPhotosJob?.cancel()
        ownerId = null
        photoLoader.reset()
        deletedMomentIds.clear()
        savedState["journal_owner"] = null
        savedState["journal_filter"] = "all"
        _state.value = JournalUiState(error = R.string.journal_session_error)
    }

    override fun onCleared() {
        networkWatch.close()
    }
}
