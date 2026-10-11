package com.example.momento.feature.moment.domain.repository

import com.example.momento.feature.moment.domain.model.AudienceType
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MomentReaction
import java.io.File
import kotlinx.serialization.json.JsonObject

interface MomentRepository {
    /** Omit cursor on the first page; pass back the server's nextCursor unchanged. */
    suspend fun getFeed(cursor: String? = null, limit: Int = 10): Result<CursorPage<Moment>>

    suspend fun getFriendFeed(
        friendId: String,
        cursor: String? = null,
        limit: Int = 10,
    ): Result<CursorPage<Moment>>

    suspend fun getMyMoments(cursor: String? = null, limit: Int = 10): Result<CursorPage<Moment>>

    /** CUSTOM with no viewers saves for the owner only. Uploads are never retried here. */
    suspend fun uploadMoment(
        image: File,
        mimeType: String,
        audienceType: AudienceType,
        caption: String? = null,
        audienceUserIds: List<String> = emptyList(),
        metadata: JsonObject? = null,
    ): Result<Moment>

    /** Each call creates a new reaction; the backend does not implement a heart toggle. */
    suspend fun reactToMoment(momentId: String, emoji: String): Result<Unit>

    /** Owner-only operation that also marks the returned reactions as seen. */
    suspend fun viewUnseenReactions(momentId: String): Result<List<MomentReaction>>

    suspend fun deleteMoment(momentId: String): Result<Unit>
}
