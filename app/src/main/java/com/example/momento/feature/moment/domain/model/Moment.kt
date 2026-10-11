package com.example.momento.feature.moment.domain.model

import java.time.Instant
import kotlinx.serialization.json.JsonElement

enum class AudienceType {
    ALL_FRIENDS,
    CUSTOM,
}

/** Profile enrichment is separate because PhotoResponse only provides ownerId. */
data class MomentOwner(
    val id: String,
    val username: String?,
    val displayName: String?,
    val avatarUrl: String?,
)

data class Moment(
    val id: String,
    val ownerId: String,
    val imageUrl: String,
    val caption: String?,
    val audienceType: AudienceType,
    val metadata: JsonElement?,
    val createdAt: Instant,
)

data class GalleryImage(val id: String, val uri: String)

data class CursorPage<T>(
    val data: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

data class MomentReaction(
    val id: String,
    val photoId: String,
    val userId: String,
    val emoji: String,
    val createdAt: Instant,
)