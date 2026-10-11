package com.example.momento.feature.moment.presentation.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.momento.R
import com.example.momento.feature.moment.domain.model.GalleryImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(
    state: GalleryUiState,
    onDismiss: () -> Unit,
    onImageSelected: (String) -> Unit,
    onConfirm: (GalleryImage) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.82f).padding(horizontal = 20.dp).testTag("moment_gallery")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.gallery_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(onDismiss) {
                    Icon(Icons.Outlined.Close, stringResource(R.string.camera_cancel), Modifier.size(24.dp))
                }
            }
            if (state.access == GalleryAccess.LIMITED) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(stringResource(R.string.gallery_limited), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onRequestAccess) { Text(stringResource(R.string.gallery_manage_access)) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                GalleryContent(state, onImageSelected, onRequestAccess, onOpenSettings, onRetry)
            }
            val canChoose = state.access in listOf(GalleryAccess.FULL, GalleryAccess.LIMITED) && !state.isLoading && !state.hasError
            val selected = state.images.firstOrNull { it.id == state.selectedImageId }
            Button(onClick = { selected?.let(onConfirm) }, enabled = canChoose && selected != null,
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).testTag("gallery_confirm")) {
                Text(stringResource(R.string.gallery_choose))
            }
        }
    }
}

@Composable
private fun GalleryContent(
    state: GalleryUiState,
    onImageSelected: (String) -> Unit,
    onRequestAccess: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.access == GalleryAccess.UNAVAILABLE -> GalleryMessage(stringResource(R.string.gallery_unavailable))
        state.access == GalleryAccess.REQUIRED -> GalleryMessage(
            message = stringResource(R.string.gallery_permission_title),
            detail = stringResource(R.string.gallery_permission_message),
            actionLabel = stringResource(R.string.gallery_permission_action),
            onAction = onRequestAccess,
        )
        state.access == GalleryAccess.DENIED -> GalleryMessage(
            message = stringResource(R.string.gallery_permission_denied),
            actionLabel = stringResource(R.string.gallery_settings),
            onAction = onOpenSettings,
        )
        state.isLoading -> CircularProgressIndicator()
        state.hasError -> GalleryMessage(
            message = stringResource(R.string.gallery_error),
            actionLabel = stringResource(R.string.camera_retry),
            onAction = onRetry,
        )
        state.images.isEmpty() -> GalleryMessage(stringResource(R.string.gallery_empty))
        else -> GalleryGrid(state.images, state.selectedImageId, onImageSelected)
    }
}

@Composable
private fun GalleryGrid(
    images: List<GalleryImage>,
    selectedImageId: String?,
    onImageSelected: (String) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag("gallery_grid"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(images, key = { _, image -> image.id }) { index, image ->
            GalleryImageItem(
                image = image,
                position = index + 1,
                selected = image.id == selectedImageId,
                onClick = { onImageSelected(image.id) },
            )
        }
    }
}

@Composable
private fun GalleryImageItem(image: GalleryImage, position: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val description = stringResource(R.string.gallery_image_description, position)
    val selectionBorder = if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape) else Modifier
    Box(
        Modifier.aspectRatio(1f).clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .testTag("gallery_image_${image.id}").semantics { contentDescription = description }
            .then(selectionBorder),
    ) {
        AsyncImage(image.uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (selected) Box(
            Modifier.align(Alignment.TopEnd).padding(7.dp).size(24.dp)
                .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                .clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Check, null, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
private fun GalleryMessage(message: String, detail: String? = null, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(36.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        actionLabel?.let { Button(onAction) { Text(it) } }
    }
}
