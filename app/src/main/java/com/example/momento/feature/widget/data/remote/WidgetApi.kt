package com.example.momento.feature.widget.data.remote

import com.example.momento.feature.moment.data.remote.dto.PhotoResponseDto
import kotlinx.serialization.Serializable
import retrofit2.http.GET

interface WidgetApi {
    // Unlike friends/profile, this endpoint returns an array without BaseResponse.
    @GET("widget")
    suspend fun getPhotos(): List<WidgetItemDto>
}

@Serializable
data class WidgetItemDto(
    val friendId: String,
    val photo: PhotoResponseDto,
)
