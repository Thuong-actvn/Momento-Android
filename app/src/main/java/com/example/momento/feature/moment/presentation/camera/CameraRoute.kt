package com.example.momento.feature.moment.presentation.camera

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.feature.moment.presentation.gallery.GallerySheet
import com.example.momento.feature.moment.presentation.gallery.GalleryViewModel
import com.example.momento.feature.moment.presentation.gallery.ObserveGalleryChanges
import com.example.momento.feature.moment.presentation.gallery.galleryAccess
import com.example.momento.feature.moment.presentation.gallery.galleryPermissions
import com.example.momento.feature.moment.presentation.gallery.hasPermission

@Composable
fun CameraRoute(
    modifier: Modifier = Modifier,
    ownAvatarUrl: String? = null,
    viewModel: CameraViewModel = hiltViewModel(),
    galleryViewModel: GalleryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val draft by viewModel.state.collectAsStateWithLifecycle()
    val gallery by galleryViewModel.state.collectAsStateWithLifecycle()
    var showGallery by rememberSaveable { mutableStateOf(false) }
    var askedCamera by rememberSaveable { mutableStateOf(false) }
    var askedGallery by rememberSaveable { mutableStateOf(false) }
    var hasCameraPermission by remember { mutableStateOf(context.hasPermission(Manifest.permission.CAMERA)) }
    var cameraDenied by remember { mutableStateOf(false) }
    var cameraRestart by remember { mutableIntStateOf(0) }
    val session = remember(context, cameraRestart) { CameraSession(context) }
    LaunchedEffect(draft.error, session) {
        if (draft.error != null) session.clearCapturedPreview()
    }

    fun permanentlyDenied(permission: String, asked: Boolean): Boolean = asked && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)

    fun refreshCameraPermission() {
        hasCameraPermission = context.hasPermission(Manifest.permission.CAMERA)
        cameraDenied = !hasCameraPermission && permanentlyDenied(Manifest.permission.CAMERA, askedCamera)
    }

    fun refreshGallery() {
        val denied = askedGallery && galleryPermissions().all { permanentlyDenied(it, true) }
        galleryViewModel.refresh(galleryAccess(context, denied))
    }

    fun openSettings() {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
    }

    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refreshCameraPermission()
    }
    val libraryPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshGallery() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        refreshCameraPermission()
    }
    ObserveGalleryChanges(onRefresh = ::refreshGallery)

    val controls = session.controls
    val state = draft.copy(
        previewStatus = when {
            hasCameraPermission -> controls.status
            cameraDenied -> CameraPreviewStatus.PERMISSION_DENIED
            else -> CameraPreviewStatus.PERMISSION_REQUIRED
        },
        hasFlash = controls.hasFlash,
        flashEnabled = controls.flashEnabled,
        isFrontCamera = controls.isFrontCamera,
        canSwitchCamera = controls.canSwitchCamera,
        zoomRatio = controls.zoomRatio,
        supportedZoomRatios = controls.supportedZoomRatios,
        latestImage = gallery.images.firstOrNull()?.uri,
    )
    val showViewfinder = hasCameraPermission && (!draft.hasDraft || session.capturedPreview != null)

    fun captureMoment() {
        if (!state.canCapture) return
        val file = viewModel.beginCapture() ?: return
        session.capture(file, viewModel::captureSucceeded, viewModel::captureFailed)
    }

    fun openGallery() {
        galleryViewModel.clearSelection()
        refreshGallery()
        showGallery = true
    }

    CameraScreen(
        state = state,
        actions = CameraActions(
            onCapture = ::captureMoment,
            onDiscard = viewModel::discardDraft,
            onCaptionChanged = viewModel::changeCaption,
            onAudienceSelected = viewModel::selectAudience,
            onGallery = ::openGallery,
            onFlashToggled = session::toggleFlash,
            onCameraSwitched = session::switchCamera,
            onZoomSelected = session::selectZoom,
            onRetryCamera = { cameraRestart++ },
            onRequestCameraPermission = {
                if (cameraDenied) openSettings() else {
                    askedCamera = true
                    cameraPermission.launch(Manifest.permission.CAMERA)
                }
            },
        ),
        ownAvatarUrl = ownAvatarUrl,
        modifier = modifier,
        viewfinder = {
            if (showViewfinder) CameraViewfinder(session)
        },
    )
    if (showGallery) GallerySheet(
        state = gallery,
        onDismiss = { showGallery = false },
        onImageSelected = galleryViewModel::selectImage,
        onConfirm = { image ->
            showGallery = false
            viewModel.chooseImage(image.uri.toUri())
        },
        onRequestAccess = {
            askedGallery = true
            libraryPermission.launch(galleryPermissions())
        },
        onOpenSettings = ::openSettings,
        onRetry = ::refreshGallery,
    )
}
