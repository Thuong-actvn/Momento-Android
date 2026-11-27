package com.example.momento.feature.moment.data.repository

import android.graphics.Bitmap
import android.util.Log
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.network.ApiException
import com.example.momento.core.realtime.RealtimeNetworkMonitor
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.data.local.MomentImageStore
import com.example.momento.feature.moment.data.local.MomentMemoryStore
import com.example.momento.feature.moment.data.local.MomentCacheStore
import com.example.momento.feature.moment.data.remote.MomentApi
import com.example.momento.feature.moment.data.remote.MomentError
import com.example.momento.feature.moment.data.remote.MomentException
import com.example.momento.feature.moment.data.remote.dto.ReactionRequestDto
import com.example.momento.feature.moment.data.remote.dto.toDomain
import com.example.momento.feature.moment.data.remote.toMomentException
import com.example.momento.feature.moment.domain.model.AudienceType
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MomentReaction
import com.example.momento.feature.moment.domain.model.MyJournalPhoto
import com.example.momento.feature.moment.domain.repository.MomentRepository
import dagger.Lazy
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink

@Singleton
class MomentRepositoryImpl @Inject constructor(
    private val api: MomentApi,
    private val userStore: CurrentUserStore,
    private val memory: MomentMemoryStore,
    private val network: RealtimeNetworkMonitor,
    private val journal: Lazy<MomentCacheStore>,
) : MomentRepository {
    override val cachedMoments = memory.moments
    override val myJournalPhotos get() = journal.get().photos

    override suspend fun getCachedJournalPhotos(ownerId: String?): Result<List<Moment>> = requestResult { session ->
        journal.get().getCachedPhotos(session, ownerId, includeOwn = true)
    }

    private val journalPageLock = Mutex()
    private var journalPagingGeneration: Long? = null
    private val usedJournalCursors = mutableSetOf<String>()

    override suspend fun getMoment(photoId: String): Result<Moment> = requestResult { session ->
        val id = validateId(photoId)
        try {
            if (!canUseNetwork()) throw MomentException(MomentError.NETWORK)
            val moment = fetchMoment(id)
            memory.merge(session, listOf(moment))
            cacheSafely { journal.get().savePhoto(session, moment) }
            moment
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val failure = error.toMomentException()
            if (failure.backendCode == "PHOTO_NOT_FOUND" || failure.backendCode == "PHOTO_ACCESS_DENIED") {
                removeJournalPhoto(session, id)
            }
            if (failure.reason == MomentError.NETWORK) {
                val cached = journal.get().getPhoto(session, id)
                if (cached != null) return@requestResult cached.moment
            }
            throw failure
        }
    }

    override suspend fun openJournalPhoto(photo: Moment): Result<MyJournalPhoto> = requestResult { session ->
        val id = validateId(photo.id)
        checkPhotoWasNotRemoved(session, id)

        if (!canUseNetwork()) return@requestResult offlineJournalPhoto(session, photo, id)

        val freshPhoto = try {
            val response = fetchMoment(id)
            if (response.ownerId != photo.ownerId) {
                throw MomentException(MomentError.REQUEST_FAILED, backendCode = "PHOTO_ACCESS_DENIED")
            }
            response
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkSession(session)
            val failure = error.toMomentException()
            if (failure.backendCode == "PHOTO_NOT_FOUND" || failure.backendCode == "PHOTO_ACCESS_DENIED") {
                removeJournalPhoto(session, id)
            }
            if (failure.reason == MomentError.NETWORK) {
                return@requestResult offlineJournalPhoto(session, photo, id)
            }
            throw failure
        }

        checkSession(session)
        checkPhotoWasNotRemoved(session, id)
        val store = journal.get()
        val preview = suspendRunCatching {
            val bitmap = store.downloadPreview(freshPhoto.imageUrl)
            store.savePhoto(session, freshPhoto, bitmap)
        }.onFailure { error ->
            Log.w("MomentoJournal", "Unable to cache the opened Journal photo", error)
        }.getOrNull()
        checkPhotoWasNotRemoved(session, id)
        memory.merge(session, listOf(freshPhoto))
        MyJournalPhoto(moment = freshPhoto, preview = preview, isOffline = !network.hasNetwork())
    }

    override suspend fun saveMomentPreview(photo: Moment, bitmap: Bitmap): File? {
        val session = userStore.sessionOrNull() ?: return null
        return suspendRunCatching { journal.get().saveLoadedPreview(session, photo, bitmap) }
            .onFailure { Log.w("MomentoJournal", "Unable to save the displayed photo", it) }
            .getOrNull()
    }

    private fun canUseNetwork(): Boolean = network.hasNetwork() && !userStore.isUsingCachedSession()

    private suspend fun offlineJournalPhoto(
        session: CurrentUserStore.Session,
        photo: Moment,
        id: String,
    ): MyJournalPhoto {
        val cached = journal.get().getPhoto(session, id)
        checkPhotoWasNotRemoved(session, id)
        return if (cached == null) {
            MyJournalPhoto(moment = photo.copy(preview = null), isOffline = true)
        } else {
            cached.copy(isOffline = true)
        }
    }

    private fun checkPhotoWasNotRemoved(session: CurrentUserStore.Session, id: String) {
        if (id in memory.removedPhotoIds(session)) {
            throw MomentException(MomentError.REQUEST_FAILED, backendCode = "PHOTO_NOT_FOUND")
        }
    }

    private suspend fun fetchMoment(id: String): Moment {
        val response = api.getPhoto(id)
        return decodeResponse {
            val photo = response.toDomain()
            require(photo.id == id) { "Photo ID does not match the request" }
            photo
        }
    }

    override suspend fun getFeed(cursor: String?, limit: Int): Result<CursorPage<Moment>> = requestResult { session ->
        loadCachedPage(session, cursor, ownerId = null, includeOwn = false) {
            validatePage(cursor, limit)
            val response = api.getFeed(cursor, limit)
            decodeResponse { response.toDomain() }
        }
    }

    override suspend fun getFriendFeed(
        friendId: String,
        cursor: String?,
        limit: Int,
    ): Result<CursorPage<Moment>> = requestResult { session ->
        val id = validateId(friendId)
        loadCachedPage(session, cursor, ownerId = id) {
            validatePage(cursor, limit)
            val response = api.getFriendFeed(id, cursor, limit)
            decodeResponse { response.toDomain() }
        }
    }

    override suspend fun getMyMoments(cursor: String?, limit: Int): Result<CursorPage<Moment>> = requestResult { session ->
        loadCachedPage(session, cursor, ownerId = session.userId) {
            fetchMyMoments(cursor, limit)
        }
    }

    private suspend fun loadCachedPage(
        session: CurrentUserStore.Session,
        cursor: String?,
        ownerId: String?,
        includeOwn: Boolean = true,
        fetch: suspend () -> CursorPage<Moment>,
    ): CursorPage<Moment> {
        try {
            if (!canUseNetwork()) throw MomentException(MomentError.NETWORK)
            val page = fetch()
            if (ownerId != null && page.data.any { it.ownerId != ownerId }) {
                throw MomentException(MomentError.INVALID_RESPONSE)
            }
            memory.merge(session, page.data)
            cacheSafely { journal.get().savePage(session, page, saveOwnCursor = false) }
            return page
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val failure = error.toMomentException()
            if (failure.backendCode == "NOT_FRIENDS" && ownerId != null && ownerId != session.userId) {
                cacheSafely { journal.get().removeFriend(session, ownerId) }
            }
            if (failure.reason == MomentError.NETWORK && cursor == null) {
                val cached = journal.get().getCachedPhotos(session, ownerId, includeOwn)
                if (cached.isNotEmpty()) return CursorPage(cached, null, false, fromCache = true)
            }
            throw failure
        }
    }

    private suspend fun cacheSafely(write: suspend () -> Unit) {
        suspendRunCatching { write() }.onFailure { error ->
            Log.w("MomentoJournal", "Unable to update the moment cache", error)
        }
    }

    override suspend fun refreshMyJournal(): Result<Boolean> = syncMyJournal(refresh = true)

    override suspend fun loadMoreMyJournal(): Result<Boolean> = syncMyJournal(refresh = false)

    private suspend fun syncMyJournal(refresh: Boolean): Result<Boolean> = requestResult { session ->
        journalPageLock.withLock {
            checkSession(session)
            if (journalPagingGeneration != session.generation) {
                journalPagingGeneration = session.generation
                usedJournalCursors.clear()
            }

            val store = journal.get()
            val cursor = if (refresh) {
                null
            } else {
                val state = store.getSyncState(session)
                if (state?.hasMore != true) return@withLock false
                state.nextCursor ?: return@withLock false
            }

            checkSession(session)
            val response = fetchMyMoments(cursor)
            val page = decodeResponse {
                require(response.data.all { it.ownerId == session.userId }) {
                    "The personal photo page contains another account's photos"
                }
                if (response.hasMore) {
                    val next = response.nextCursor
                    require(!next.isNullOrBlank() && next != cursor && response.data.isNotEmpty()) {
                        "Invalid next cursor for personal photos"
                    }
                    require(refresh || next !in usedJournalCursors) { "Personal photo cursor repeated" }
                }
                val photos = response.data.map { photo -> photo.copy(id = validateId(photo.id)) }
                response.copy(data = photos)
            }

            store.savePage(session, page)
            if (refresh) usedJournalCursors.clear()
            if (cursor != null) usedJournalCursors.add(cursor)
            memory.merge(session, page.data)
            page.hasMore
        }
    }

    private suspend fun fetchMyMoments(cursor: String?, limit: Int = 10): CursorPage<Moment> {
        validatePage(cursor, limit)
        val response = api.getMyPhotos(cursor, limit)
        return decodeResponse { response.toDomain() }
    }

    override suspend fun uploadMoment(
        image: File,
        mimeType: String,
        audienceType: AudienceType,
        caption: String?,
        audienceUserIds: List<String>,
        metadata: JsonObject?,
    ): Result<Moment> = requestResult { session ->
        if (!image.isFile || image.length() == 0L) throw MomentException(MomentError.INVALID_IMAGE)
        if (image.length() > MomentImageStore.MAX_IMAGE_BYTES) {
            throw MomentException(MomentError.IMAGE_TOO_LARGE)
        }
        val extension = when (mimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> throw MomentException(MomentError.INVALID_IMAGE)
        }
        val recipients = if (audienceType == AudienceType.CUSTOM) {
            // The backend accepts an empty CUSTOM audience as owner-only visibility.
            audienceUserIds.map(::validateId).distinct()
        } else {
            null
        }
        val metadataJson = metadata?.toString()
        require(metadataJson == null || metadataJson.length <= 5120) {
            "Metadata must not exceed 5120 characters"
        }
        val body = image.asRequestBody(mimeType.toMediaType())
        // Once any upload body has been sent, a network failure must not replay the POST.
        val uploadBody = body.oneShot()
        val part = MultipartBody.Part.createFormData("image", "moment.$extension", uploadBody)
        val response = api.uploadPhoto(
            image = part,
            caption = caption?.trim()?.takeIf { it.isNotEmpty() },
            audienceType = audienceType.name,
            audienceUserIds = recipients,
            metadata = metadataJson,
        )
        val photo = decodeResponse { response.toDomain() }
        memory.merge(session, listOf(photo))
        withContext(NonCancellable) {
            suspendRunCatching {
                val id = validateId(photo.id)
                journal.get().savePhoto(session, photo.copy(id = id))
            }.onFailure { error ->
                Log.w("MomentoJournal", "Unable to cache the uploaded photo", error)
            }
        }
        photo
    }

    override suspend fun reactToMoment(momentId: String, emoji: String): Result<Unit> = requestResult {
        validateId(momentId)
        require(emoji.isNotBlank() && emoji.length <= 10) {
            "Reaction must be nonblank and at most 10 characters"
        }
        val body = Json.encodeToString(ReactionRequestDto(emoji))
            .toRequestBody("application/json; charset=utf-8".toMediaType())
            .oneShot()
        val response = api.reactToPhoto(momentId, body)
        // BaseResponse<Void> deliberately omits data on success.
        if (!response.success) {
            throw ApiException(response.error?.code, response.error?.message ?: "Reaction failed")
        }
    }

    override suspend fun getReactions(momentId: String): Result<List<MomentReaction>> = requestResult {
        validateId(momentId)
        val response = api.getReactions(momentId)
        decodeResponse { response.requireData().map { it.toDomain() } }
    }

    override suspend fun viewUnseenReactions(momentId: String): Result<List<MomentReaction>> = requestResult {
        validateId(momentId)
        val response = api.viewUnseenReactions(momentId)
        decodeResponse { response.requireData().map { it.toDomain() } }
    }

    override suspend fun deleteMoment(momentId: String): Result<Unit> = requestResult { session ->
        val id = validateId(momentId)
        api.deletePhoto(id)
        removeJournalPhoto(session, id)
    }

    private suspend fun removeJournalPhoto(session: CurrentUserStore.Session, id: String) {
        withContext(NonCancellable) {
            memory.remove(session, id)
            // A cache error must not turn a confirmed server deletion into a failed delete.
            suspendRunCatching { journal.get().deletePhoto(session, id) }.onFailure { error ->
                Log.w("MomentoJournal", "Unable to remove the deleted photo from Room", error)
            }
        }
    }

    private fun validatePage(cursor: String?, limit: Int) {
        require(limit in 1..50) { "Page limit must be between 1 and 50" }
        require(cursor == null || cursor.isNotBlank()) { "Omit cursor on the first page" }
    }

    private fun validateId(value: String): String {
        val canonicalId = UUID.fromString(value).toString()
        require(canonicalId.equals(value, ignoreCase = true)) {
            "Expected a backend UUID"
        }
        return canonicalId
    }

    private fun <T> decodeResponse(block: () -> T): T = try {
        block()
    } catch (error: ApiException) {
        throw error
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw MomentException(MomentError.INVALID_RESPONSE, cause = error)
    }

    private suspend fun <T> requestResult(block: suspend (CurrentUserStore.Session) -> T): Result<T> = suspendRunCatching {
        val session = try {
            userStore.requireSession()
        } catch (error: IllegalStateException) {
            throw MomentException(MomentError.UNAUTHENTICATED, cause = error)
        }
        try {
            block(session).also { checkSession(session) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            checkSession(session)
            throw error.toMomentException()
        }
    }

    private fun checkSession(session: CurrentUserStore.Session) {
        try {
            userStore.checkSession(session)
        } catch (error: IllegalStateException) {
            throw MomentException(MomentError.SESSION_CHANGED, cause = error)
        }
    }
}

// OkHttp must not replay a POST after this body has been sent.
private fun RequestBody.oneShot(): RequestBody {
    val body = this
    return object : RequestBody() {
        override fun contentType() = body.contentType()
        override fun contentLength() = body.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
    }
}
