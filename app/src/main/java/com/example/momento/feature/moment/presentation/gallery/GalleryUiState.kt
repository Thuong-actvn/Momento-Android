package com.example.momento.feature.moment.presentation.gallery

data class GalleryImage(val id: String, val uri: String)

enum class GalleryAccess { UNAVAILABLE, REQUIRED, DENIED, LIMITED, FULL }

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val access: GalleryAccess = GalleryAccess.UNAVAILABLE,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val selectedImageId: String? = null,
)
