package com.example.momento.feature.moment.data.repository

import com.example.momento.core.common.suspendRunCatching
import com.example.momento.core.network.ApiException
import com.example.momento.core.user.data.CurrentUserStore
import com.example.momento.feature.moment.data.local.MomentImageStore
import com.example.momento.feature.moment.data.local.MomentMemoryStore
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
import com.example.momento.feature.moment.domain.repository.MomentRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
) : MomentRepository {
    override val cachedMoments = memory.moments

    override suspend fun getMoment(photoId: String): Result<Moment> = requestResult { session ->
        val id = validateId(photoId)
        try {
            val response = api.getPhoto(id)
            val moment = decodeResponse {
                response.toDomain().also { require(it.id == id) { "Photo ID does not match the request" } }
            }
            memory.merge(session, listOf(moment))
            moment
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val failure = error.toMomentException()
            when (failure.backendCode) {
                "PHOTO_NOT_FOUND" -> memory.remove(session, id)
                "PHOTO_ACCESS_DENIED" -> memory.evict(session, id)
            }
            throw failure
        }
    }

    override suspend fun getFeed(cursor: String?, limit: Int): Result<CursorPage<Moment>> = requestResult { session ->
        validatePage(cursor, limit)
        val response = api.getFeed(cursor, limit)
        decodeResponse { response.toDomain() }.also { memory.merge(session, it.data) }
    }

    override suspend fun getFriendFeed(
        friendId: String,
        cursor: String?,
        limit: Int,
    ): Result<CursorPage<Moment>> = requestResult { session ->
        validateId(friendId)
        validatePage(cursor, limit)
        val response = api.getFriendFeed(friendId, cursor, limit)
        decodeResponse { response.toDomain() }.also { memory.merge(session, it.data) }
    }

    override suspend fun getMyMoments(cursor: String?, limit: Int): Result<CursorPage<Moment>> = requestResult { session ->
        validatePage(cursor, limit)
        val response = api.getMyPhotos(cursor, limit)
        decodeResponse { response.toDomain() }.also { memory.merge(session, it.data) }
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
        decodeResponse { response.toDomain() }.also { memory.merge(session, listOf(it)) }
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

    override suspend fun viewUnseenReactions(momentId: String): Result<List<MomentReaction>> = requestResult {
        validateId(momentId)
        val response = api.viewUnseenReactions(momentId)
        decodeResponse { response.requireData().map { it.toDomain() } }
    }

    override suspend fun deleteMoment(momentId: String): Result<Unit> = requestResult { session ->
        validateId(momentId)
        api.deletePhoto(momentId)
        memory.remove(session, momentId)
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
