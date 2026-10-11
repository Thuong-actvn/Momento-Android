package com.example.momento.feature.moment.presentation.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momento.R
import com.example.momento.feature.moment.presentation.components.MOMENT_IMAGE_ASPECT_RATIO
import com.example.momento.feature.moment.presentation.components.MomentImage
import com.example.momento.feature.moment.presentation.components.MomentImageFrame
import com.example.momento.ui.theme.MomentoTheme

/** UI callbacks; camera, storage and upload lifecycles belong to the route. */
data class CameraActions(
    val onCapture: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onGallery: () -> Unit = {},
    val onCaptionChanged: (String) -> Unit = {},
    val onAudienceSelected: (CameraAudience) -> Unit = {},
    val onFriendToggled: (String) -> Unit = {},
    val onFlashToggled: () -> Unit = {},
    val onCameraSwitched: () -> Unit = {},
    val onZoomSelected: (Float) -> Unit = {},
    val onRequestCameraPermission: () -> Unit = {},
    val onRetryCamera: () -> Unit = {},
    val onRetryRecipients: () -> Unit = {},
)

@Composable
fun CameraScreen(
    state: CameraUiState,
    actions: CameraActions,
    modifier: Modifier = Modifier,
    ownAvatarUrl: String? = null,
    viewfinder: @Composable BoxScope.() -> Unit = {},
) {
    var imageFailed by remember(state.draftImage) { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().testTag("camera_screen")) {
        // Keep the same 4:5 frame on short displays, with the rest of the form scrollable.
        val frameWidth = minOf(maxWidth - 40.dp, maxHeight * 0.58f * MOMENT_IMAGE_ASPECT_RATIO).coerceAtLeast(160.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            CameraImageFrame(
                image = state.draftImage,
                imageFailed = imageFailed,
                onImageErrorChanged = { imageFailed = it },
                modifier = Modifier.width(frameWidth),
            ) {
                viewfinder()
                if (!state.hasDraft) {
                    CameraPreviewMessage(state.previewStatus, actions)
                    CameraOverlayControls(state, actions)
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MomentCaption(state.caption, actions.onCaptionChanged, enabled = !state.isSending)
                DeferredMomentOptions()
                MomentRecipients(state, ownAvatarUrl, actions.onAudienceSelected, actions.onFriendToggled, actions.onRetryRecipients)
                state.error?.let {
                    Text(stringResource(it), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (state.isSending) Text(stringResource(R.string.camera_sending),
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                MomentActionRow(
                    hasDraft = state.hasDraft,
                    latestImage = state.latestImage,
                    isSending = state.isSending,
                    actionEnabled = if (state.hasDraft) state.canSend && !imageFailed else state.canCapture,
                    actionDescription = stringResource(when {
                        !state.hasDraft -> R.string.camera_capture
                        state.selection.audience == CameraAudience.ONLY_ME -> R.string.camera_save_private
                        else -> R.string.camera_send
                    }),
                    onGallery = actions.onGallery,
                    onAction = if (state.hasDraft) actions.onSend else actions.onCapture,
                    onClose = if (state.hasDraft) actions.onDiscard else actions.onClose,
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CameraImageFrame(
    image: String?,
    imageFailed: Boolean,
    onImageErrorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cameraContent: @Composable BoxScope.() -> Unit,
) {
    var imageLoaded by remember(image) { mutableStateOf(false) }
    MomentImageFrame(modifier.testTag("moment_image_frame")) {
        // Keep the frozen viewfinder underneath until Coil can draw the captured photo.
        if (image == null || (!imageLoaded && !imageFailed)) cameraContent()
        if (image != null) {
            MomentImage(
                image = image,
                contentDescription = stringResource(R.string.camera_image_description),
                modifier = Modifier.fillMaxSize(),
                onError = { onImageErrorChanged(true) },
                onSuccess = { imageLoaded = true; onImageErrorChanged(false) },
            )
            if (imageFailed) FrameMessage(stringResource(R.string.camera_image_error))
        }
    }
}

@Composable
private fun BoxScope.CameraPreviewMessage(status: CameraPreviewStatus?, actions: CameraActions) {
    if (status == null || status == CameraPreviewStatus.READY) return
    val needsPermission = status == CameraPreviewStatus.PERMISSION_REQUIRED || status == CameraPreviewStatus.PERMISSION_DENIED
    Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (needsPermission) 8.dp else 12.dp)) {
        Icon(painterResource(R.drawable.ic_main_camera), null, Modifier.size(if (needsPermission) 32.dp else 40.dp),
            MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(when (status) {
            CameraPreviewStatus.PERMISSION_REQUIRED -> R.string.camera_permission_title
            CameraPreviewStatus.PERMISSION_DENIED -> R.string.camera_permission_denied
            CameraPreviewStatus.ERROR -> R.string.camera_error
            else -> R.string.camera_unavailable
        }), style = if (needsPermission) MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
            else MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        when (status) {
            CameraPreviewStatus.PERMISSION_REQUIRED, CameraPreviewStatus.PERMISSION_DENIED -> {
                Text(stringResource(R.string.camera_permission_message),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Button(actions.onRequestCameraPermission, modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(if (status == CameraPreviewStatus.PERMISSION_DENIED) R.string.gallery_settings
                        else R.string.camera_permission_action), style = MaterialTheme.typography.labelMedium)
                }
            }
            CameraPreviewStatus.ERROR -> TextButton(actions.onRetryCamera) {
                Text(stringResource(R.string.camera_retry))
            }
            else -> Unit
        }
    }
}

@Composable
private fun BoxScope.FrameMessage(message: String) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Text(message, Modifier.padding(24.dp), color = Color.White, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium)
    }
}

@Preview(showSystemUi = true)
@Composable
private fun CameraCapturePreview() {
    CameraPreviewContent(CameraUiState(previewStatus = CameraPreviewStatus.READY, hasFlash = true,
        canSwitchCamera = true, supportedZoomRatios = listOf(0.5f, 1f, 2f), recipients = sampleRecipients))
}

@Composable
private fun CameraPreviewContent(initialState: CameraUiState) {
    var state by remember { mutableStateOf(initialState) }
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        CameraScreen(state, CameraActions(
            onCaptionChanged = { state = state.copy(caption = it) },
            onAudienceSelected = { state = state.copy(selection = state.selection.select(it)) },
            onFriendToggled = { state = state.copy(selection = state.selection.toggleFriend(it)) },
            onDiscard = { state = state.copy(draftImage = null, caption = "") },
            onFlashToggled = { state = state.copy(flashEnabled = !state.flashEnabled) },
            onCameraSwitched = { state = state.copy(isFrontCamera = !state.isFrontCamera) },
            onZoomSelected = { state = state.copy(zoomRatio = it) },
        ))
    }
}

private val sampleRecipients = listOf(CameraRecipient("emma", "Emma"), CameraRecipient("maya", "Maya"),
    CameraRecipient("sam", "Sam"), CameraRecipient("chloe", "Chloe"))
