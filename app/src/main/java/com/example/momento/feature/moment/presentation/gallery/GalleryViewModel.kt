package com.example.momento.feature.moment.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.momento.core.common.suspendRunCatching
import com.example.momento.feature.moment.data.local.MomentGallerySource
import com.example.momento.feature.moment.domain.model.GalleryImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GalleryAccess { UNAVAILABLE, REQUIRED, DENIED, LIMITED, FULL }

data class GalleryUiState(
    val images: List<GalleryImage> = emptyList(),
    val access: GalleryAccess = GalleryAccess.UNAVAILABLE,
    val isLoading: Boolean = false,
    val hasError: Boolean = false,
    val selectedImageId: String? = null,
)

@HiltViewModel
class GalleryViewModel @Inject constructor(private val source: MomentGallerySource) : ViewModel() {
    private val _state = MutableStateFlow(GalleryUiState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun refresh(access: GalleryAccess) {
        loadJob?.cancel()
        if (access != GalleryAccess.FULL && access != GalleryAccess.LIMITED) {
            _state.value = GalleryUiState(access = access)
            return
        }
        _state.update {
            if (it.access != access) GalleryUiState(access = access, isLoading = true)
            else it.copy(isLoading = true, hasError = false)
        }
        loadJob = viewModelScope.launch {
            suspendRunCatching { source.recentImages() }
                .onSuccess(::showImages)
                .onFailure(::showLoadError)
        }
    }

    private fun showImages(images: List<GalleryImage>) {
        _state.update { current ->
            val selectionExists = images.any { it.id == current.selectedImageId }
            current.copy(
                images = images,
                isLoading = false,
                hasError = false,
                selectedImageId = if (selectionExists) current.selectedImageId else null,
            )
        }
    }

    private fun showLoadError(error: Throwable) {
        if (error is SecurityException) {
            _state.value = GalleryUiState(access = GalleryAccess.REQUIRED)
            return
        }
        _state.update {
            it.copy(isLoading = false, hasError = true, images = emptyList(), selectedImageId = null)
        }
    }

    fun selectImage(id: String) {
        if (_state.value.isLoading || _state.value.hasError) return
        if (_state.value.images.any { it.id == id }) _state.update { it.copy(selectedImageId = id) }
    }

    fun clearSelection() { _state.update { it.copy(selectedImageId = null) } }
}
