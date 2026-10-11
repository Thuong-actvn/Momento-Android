package com.example.momento.feature.moment.presentation.camera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.momento.feature.moment.presentation.gallery.GallerySheet
import com.example.momento.feature.moment.presentation.gallery.GalleryUiState

/** Phase 2 hosts UI only. Hardware, MediaStore and upload are connected in the next phases. */
@Composable
fun CameraRoute(modifier: Modifier = Modifier, ownAvatarUrl: String? = null) {
    var audience by rememberSaveable { mutableStateOf(CameraAudience.ALL_FRIENDS) }
    var showGallery by rememberSaveable { mutableStateOf(false) }

    CameraScreen(
        state = CameraUiState(selection = CameraAudienceSelection(audience)),
        actions = CameraActions(
            onAudienceSelected = { audience = it },
            onGallery = { showGallery = true },
        ),
        ownAvatarUrl = ownAvatarUrl,
        modifier = modifier,
    )
    if (showGallery) GallerySheet(
        state = GalleryUiState(),
        onDismiss = { showGallery = false },
        onImageSelected = {}, onConfirm = {}, onRequestAccess = {}, onOpenSettings = {}, onRetry = {},
    )
}
