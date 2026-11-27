package com.example.momento.feature.moment.data.local

import android.graphics.Bitmap
import androidx.room.withTransaction
import com.example.momento.core.database.MomentoDatabase
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.core.user.domain.model.PublicProfile
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MyJournalPhoto
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.json.Json

@Singleton
class MomentCacheStore @Inject constructor(
    private val database: MomentoDatabase,
    private val users: CurrentUserStore,
    private val memory: MomentMemoryStore,
    private val previews: JournalPreviewStore,
) {
    private val dao = database.momentCacheDao()
    private var blockedGeneration: Long? = null
    private val blockedOwners = mutableSetOf<String>()

    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: Flow<List<MyJournalPhoto>> = users.observeSession().flatMapLatest { session ->
        val accountId = session?.userId
        if (accountId == null) {
            flowOf(emptyList())
        } else {
            cleanExpired(session)
            combine(dao.observePhotos(accountId), memory.observeRemovedPhotoIds(session)) { rows, removedIds ->
                if (users.sessionVersion.value == session.generation) {
                    val photos = rows.filterNot { it.photoId in removedIds }.map { it.toJournalPhoto() }
                    if (users.sessionVersion.value == session.generation) photos else emptyList()
                } else {
                    emptyList()
                }
            }.onStart { emit(emptyList()) }
        }
    }.distinctUntilChanged()

    suspend fun getSyncState(session: CurrentUserStore.Session): MyJournalSyncStateEntity? {
        val accountId = checkNotNull(session.userId)
        return dao.getSyncState(accountId)
    }

    suspend fun getPhoto(session: CurrentUserStore.Session, photoId: String): MyJournalPhoto? {
        val accountId = checkNotNull(session.userId)
        cleanExpired(session)
        if (photoId in memory.removedPhotoIds(session)) return null
        val photo = dao.getPhotos(accountId, listOf(photoId)).firstOrNull()
        return photo?.toJournalPhoto()
    }

    suspend fun downloadPreview(imageUrl: String): Bitmap? = previews.loadBitmap(imageUrl)

    suspend fun savePhoto(
        session: CurrentUserStore.Session,
        photo: Moment,
        bitmap: Bitmap? = null,
    ): File? {
        var preview: File? = null
        writeForSession(session) { accountId ->
            if (photo.id in memory.removedPhotoIds(session) || isOwnerBlocked(session, photo.ownerId)) {
                return@writeForSession
            }

            val previous = dao.getPhotos(accountId, listOf(photo.id)).firstOrNull()
            var path = previous?.previewRelativePath
            if (bitmap != null) {
                path = previews.save(accountId, photo.id, bitmap) ?: path
            }
            dao.upsertPhotos(listOf(photo.toJournalRow(accountId, path)))
            trimCache(accountId)
            preview = previews.find(accountId, photo.id, path)
        }
        return preview
    }

    suspend fun savePage(session: CurrentUserStore.Session, page: CursorPage<Moment>, saveOwnCursor: Boolean = true) {
        writeForSession(session) { accountId ->
            // Retry any local deletion that failed after the server accepted it.
            val removedIds = memory.removedPhotoIds(session)
            for (ids in removedIds.chunked(500)) {
                dao.deletePhotos(accountId, ids)
            }
            if (removedIds.isNotEmpty()) previews.deletePhotos(accountId, removedIds)

            val photos = page.data.filterNot { it.id in removedIds || isOwnerBlocked(session, it.ownerId) }
            if (photos.isNotEmpty()) {
                val previous = dao.getPhotos(accountId, photos.map { it.id }).associateBy { it.photoId }
                val rows = photos.map { photo ->
                    val oldPhoto = previous[photo.id]
                    val preview = oldPhoto?.previewRelativePath
                    photo.toJournalRow(accountId, preview)
                }
                dao.upsertPhotos(rows)
            }

            if (saveOwnCursor) {
                val nextCursor = if (page.hasMore) page.nextCursor else null
                dao.upsertSyncState(MyJournalSyncStateEntity(accountId, nextCursor, page.hasMore))
            }
            trimCache(accountId)
        }
    }

    suspend fun getCachedPhotos(
        session: CurrentUserStore.Session,
        ownerId: String?,
        includeOwn: Boolean,
    ): List<Moment> {
        val accountId = checkNotNull(session.userId)
        cleanExpired(session)
        val removedIds = memory.removedPhotoIds(session)
        val photos = mutableListOf<Moment>()
        for (row in dao.getCachedPhotos(accountId, ownerId, includeOwn)) {
            if (row.photoId !in removedIds) photos.add(row.toJournalPhoto().moment)
        }
        return photos
    }

    suspend fun saveLoadedPreview(session: CurrentUserStore.Session, photo: Moment, bitmap: Bitmap): File? {
        var preview: File? = null
        writeForSession(session) { accountId ->
            if (photo.id in memory.removedPhotoIds(session) || isOwnerBlocked(session, photo.ownerId)) {
                return@writeForSession
            }
            val row = dao.getPhotos(accountId, listOf(photo.id)).firstOrNull() ?: return@writeForSession
            if (row.imageUrl != photo.imageUrl || row.ownerId != photo.ownerId) return@writeForSession
            preview = previews.find(accountId, photo.id, row.previewRelativePath)
            if (preview != null) return@writeForSession

            val path = previews.save(accountId, photo.id, bitmap) ?: return@writeForSession
            dao.updatePreview(accountId, photo.id, path)
            preview = previews.find(accountId, photo.id, path)
        }
        return preview
    }

    suspend fun saveFriends(session: CurrentUserStore.Session, friends: List<PublicProfile>) {
        writeForSession(session) { accountId ->
            val friendIds = friends.map { it.id }.toSet()
            val previousIds = dao.getFriends(accountId).map { it.id }
            for (id in previousIds) {
                if (id !in friendIds) deleteOwnerPhotos(session, accountId, id)
            }
            dao.deleteFriends(accountId)
            val cachedOwners = dao.getCachedPhotos(accountId, ownerId = null, includeOwn = false)
                .map { it.ownerId }.toSet()
            val retainedFriends = friends.filterNot { isOwnerBlocked(session, it.id) }
                .sortedBy { it.id !in cachedOwners }
                .take(MAX_CACHE_PHOTOS)
            val rows = retainedFriends.map { friend ->
                CachedMomentFriendEntity(
                    accountId, friend.id, friend.username, friend.displayName, friend.avatarUrl,
                    friend.createdAt.toString(),
                )
            }
            dao.upsertFriends(rows)
        }
    }

    suspend fun getFriends(session: CurrentUserStore.Session): List<PublicProfile> {
        val accountId = checkNotNull(session.userId)
        return dao.getFriends(accountId).map { row ->
            PublicProfile(row.id, row.username, row.displayName, row.avatarUrl, Instant.parse(row.createdAt))
        }
    }

    suspend fun removeFriend(session: CurrentUserStore.Session, ownerId: String) {
        writeForSession(session) { accountId ->
            deleteOwnerPhotos(session, accountId, ownerId)
            dao.deleteFriend(accountId, ownerId)
        }
    }

    private suspend fun deleteOwnerPhotos(session: CurrentUserStore.Session, accountId: String, ownerId: String) {
        if (blockedGeneration != session.generation) {
            blockedGeneration = session.generation
            blockedOwners.clear()
        }
        blockedOwners.add(ownerId)
        val photoIds = dao.getPhotoIdsForOwner(accountId, ownerId)
        for (photoId in photoIds) {
            memory.evict(session, photoId)
        }
        removePhotos(accountId, photoIds)
    }

    private fun isOwnerBlocked(session: CurrentUserStore.Session, ownerId: String): Boolean {
        return blockedGeneration == session.generation && ownerId in blockedOwners
    }

    private suspend fun cleanExpired(session: CurrentUserStore.Session) {
        writeForSession(session) { accountId -> trimCache(accountId) }
    }

    private suspend fun trimCache(accountId: String) {
        val cutoff = System.currentTimeMillis() - FRIEND_CACHE_MILLIS
        removePhotos(accountId, dao.getExpiredFriendPhotos(accountId, cutoff))
        removePhotos(accountId, dao.getExcessPhotos(accountId, MAX_CACHE_PHOTOS))
    }

    private suspend fun removePhotos(accountId: String, ids: List<String>) {
        if (ids.isEmpty()) return
        for (batch in ids.chunked(500)) {
            dao.deletePhotos(accountId, batch)
        }
        previews.deletePhotos(accountId, ids)
    }

    suspend fun deletePhoto(session: CurrentUserStore.Session, photoId: String) {
        writeForSession(session) { accountId ->
            dao.deletePhotos(accountId, listOf(photoId))
            previews.deletePhotos(accountId, listOf(photoId))
        }
    }

    private suspend fun writeForSession(
        session: CurrentUserStore.Session,
        write: suspend (String) -> Unit,
    ) {
        database.withTransaction {
            users.checkSession(session)
            write(checkNotNull(session.userId))
            users.checkSession(session)
        }
    }

    private suspend fun CachedMomentEntity.toJournalPhoto(): MyJournalPhoto {
        val preview = previews.find(accountId, photoId, previewRelativePath)
        return MyJournalPhoto(moment = toMoment().copy(preview = preview), preview = preview)
    }

    private companion object {
        const val MAX_CACHE_PHOTOS = 200
        const val FRIEND_CACHE_MILLIS = 14 * 24 * 60 * 60 * 1_000L
    }
}

private fun Moment.toJournalRow(accountId: String, previewRelativePath: String?) = CachedMomentEntity(
    accountId = accountId,
    photoId = id,
    imageUrl = imageUrl,
    caption = caption,
    audienceType = audienceType,
    metadataJson = metadata?.toString(),
    createdAtEpochSecond = createdAt?.epochSecond,
    createdAtNano = createdAt?.nano ?: 0,
    previewRelativePath = previewRelativePath,
    ownerId = ownerId,
    cachedAt = System.currentTimeMillis(),
)

private fun CachedMomentEntity.toMoment() = Moment(
    id = photoId,
    ownerId = ownerId,
    imageUrl = imageUrl,
    caption = caption,
    audienceType = audienceType,
    metadata = metadataJson?.let(Json::parseToJsonElement),
    createdAt = createdAtEpochSecond?.let { Instant.ofEpochSecond(it, createdAtNano.toLong()) },
)
