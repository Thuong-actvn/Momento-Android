package com.example.momento.feature.moment.data.remote.dto

import com.example.momento.feature.moment.domain.model.AudienceType
import com.example.momento.feature.moment.domain.model.CursorPage
import com.example.momento.feature.moment.domain.model.Moment
import com.example.momento.feature.moment.domain.model.MomentReaction
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class PhotoResponseDto(
    val id: String? = null,
    val ownerId: String? = null,
    val imageUrl: String? = null,
    val caption: String? = null,
    val audienceType: String? = null,
    val metadata: JsonElement? = null,
    val createdAt: String? = null,
)

@Serializable
data class CursorPagedResponsePhotoResponseDto(
    val data: List<PhotoResponseDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class ReactionRequestDto(val emoji: String)

@Serializable
data class ReactionResponseDto(
    val id: String? = null,
    val photoId: String? = null,
    val userId: String? = null,
    val emoji: String? = null,
    val createdAt: String? = null,
)

private fun required(value: String?, field: String): String =
    checkNotNull(value) { "Backend response is missing $field" }

fun PhotoResponseDto.toDomain(): Moment = Moment(
    id = required(id, "id"),
    ownerId = required(ownerId, "ownerId"),
    imageUrl = required(imageUrl, "imageUrl"),
    caption = caption,
    audienceType = runCatching {
        AudienceType.valueOf(required(audienceType, "audienceType"))
    }.getOrElse { throw IllegalArgumentException("Unknown audienceType: $audienceType", it) },
    metadata = metadata,
    createdAt = createdAt?.let(Instant::parse),
)

fun CursorPagedResponsePhotoResponseDto.toDomain(): CursorPage<Moment> = CursorPage(
    data = data.map { it.toDomain() },
    nextCursor = nextCursor,
    hasMore = hasMore,
)

fun ReactionResponseDto.toDomain(): MomentReaction = MomentReaction(
    id = required(id, "id"),
    photoId = required(photoId, "photoId"),
    userId = required(userId, "userId"),
    emoji = required(emoji, "emoji"),
    createdAt = Instant.parse(required(createdAt, "createdAt")),
)