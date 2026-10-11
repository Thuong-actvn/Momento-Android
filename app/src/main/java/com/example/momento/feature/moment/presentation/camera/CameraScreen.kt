package com.example.momento.feature.moment.presentation.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cameraswitch
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.momento.R
import com.example.momento.ui.theme.MomentoTheme

/** UI callbacks; camera, storage and upload lifecycles belong to the route. */
data class CameraActions(
    val onCapture: () -> Unit = {},
    val onSend: () -> Unit = {},
    val onDiscard: () -> Unit = {},
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
        val frameWidth = minOf(maxWidth - 40.dp, maxHeight * 0.58f * 0.8f).coerceAtLeast(160.dp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            CameraImageFrame(
                image = state.draftImage,
                imageFailed = imageFailed,
                isPreparing = state.isPreparingImage,
                onImageErrorChanged = { imageFailed = it },
                modifier = Modifier.width(frameWidth),
            ) {
                viewfinder()
                CameraPreviewMessage(state.previewStatus, actions)
                CameraOverlayControls(state, actions)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MomentCaption(state.caption, actions.onCaptionChanged, enabled = state.hasDraft && !state.isBusy)
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
                    isBusy = state.isBusy,
                    actionEnabled = if (state.hasDraft) state.canSend && !imageFailed else state.canCapture,
                    actionDescription = stringResource(when {
                        !state.hasDraft -> R.string.camera_capture
                        state.selection.audience == CameraAudience.ONLY_ME -> R.string.camera_save_private
                        else -> R.string.camera_send
                    }),
                    onGallery = actions.onGallery,
                    onAction = if (state.hasDraft) actions.onSend else actions.onCapture,
                    onDiscard = actions.onDiscard,
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
    isPreparing: Boolean,
    onImageErrorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    cameraContent: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier.aspectRatio(0.8f)
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.08f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF3A3933), Color(0xFF242622))))
            .testTag("moment_image_frame"),
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = stringResource(R.string.camera_image_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { onImageErrorChanged(true) },
                onSuccess = { onImageErrorChanged(false) },
            )
            if (imageFailed) FrameMessage(stringResource(R.string.camera_image_error))
        } else {
            cameraContent()
        }
        if (isPreparing) Column(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Color.White)
            Text(stringResource(R.string.camera_preparing), Modifier.padding(top = 12.dp),
                color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BoxScope.CameraPreviewMessage(status: CameraPreviewStatus, actions: CameraActions) {
    if (status == CameraPreviewStatus.READY) return
    val needsPermission = status == CameraPreviewStatus.PERMISSION_REQUIRED
    Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (needsPermission) 8.dp else 12.dp)) {
        Icon(painterResource(R.drawable.ic_main_camera), null, Modifier.size(if (needsPermission) 32.dp else 40.dp),
            Color.White.copy(alpha = 0.7f))
        Text(stringResource(when (status) {
            CameraPreviewStatus.PERMISSION_REQUIRED -> R.string.camera_permission_title
            CameraPreviewStatus.ERROR -> R.string.camera_error
            else -> R.string.camera_unavailable
        }), style = if (needsPermission) MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, lineHeight = 18.sp)
            else MaterialTheme.typography.titleSmall, color = Color.White, textAlign = TextAlign.Center)
        when (status) {
            CameraPreviewStatus.PERMISSION_REQUIRED -> {
                Text(stringResource(R.string.camera_permission_message),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                Button(actions.onRequestCameraPermission, modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.camera_permission_action), style = MaterialTheme.typography.labelMedium)
                }
            }
            CameraPreviewStatus.ERROR -> TextButton(actions.onRetryCamera) {
                Text(stringResource(R.string.camera_retry), color = Color.White)
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

@Composable
private fun BoxScope.CameraOverlayControls(state: CameraUiState, actions: CameraActions) {
    val flashLabel = stringResource(if (state.flashEnabled) R.string.camera_flash_off else R.string.camera_flash_on)
    val lensLabel = stringResource(if (state.isFrontCamera) R.string.camera_front else R.string.camera_back)
    CameraOverlayButton(
        icon = if (state.flashEnabled) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
        description = flashLabel,
        enabled = state.canCapture && state.hasFlash,
        available = state.hasFlash,
        onClick = actions.onFlashToggled,
        modifier = Modifier.align(Alignment.TopStart).padding(12.dp).testTag("camera_flash")
            .semantics { stateDescription = flashLabel },
    )
    CameraOverlayButton(
        icon = Icons.Outlined.Cameraswitch,
        description = stringResource(R.string.camera_switch),
        enabled = state.canCapture && state.canSwitchCamera,
        available = state.canSwitchCamera,
        onClick = actions.onCameraSwitched,
        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).testTag("camera_switch")
            .semantics { stateDescription = lensLabel },
    )
    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clip(RoundedCornerShape(30.dp))
        .background(Color.Black.copy(alpha = 0.24f)).padding(2.dp), horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        listOf(0.5f, 1f, 2f).forEach { ratio ->
            val selected = state.zoomRatio == ratio
            val supported = ratio in state.supportedZoomRatios
            val label = when (ratio) { 0.5f -> "0.5x"; 2f -> "2x"; else -> "1x" }
            val description = stringResource(R.string.camera_zoom, label)
            Box(Modifier.size(28.dp).clip(CircleShape)
                .background(if (selected) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                .selectable(selected, enabled = state.canCapture && supported, role = Role.RadioButton,
                    onClick = { actions.onZoomSelected(ratio) })
                .testTag("camera_zoom_$label").semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                Text(if (selected) label else label.removeSuffix("x"),
                    color = Color.White.copy(alpha = if (supported) 1f else 0.4f),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp))
            }
        }
    }
}

@Composable
private fun CameraOverlayButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    available: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent),
        modifier = modifier.size(48.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center) {
            Icon(icon, description, Modifier.size(20.dp),
                Color.White.copy(alpha = if (available) 1f else 0.4f))
        }
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
