package com.example.momento.feature.moment.presentation.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ZoomState
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

internal data class CameraControls(
    val status: CameraPreviewStatus? = null,
    val hasFlash: Boolean = false,
    val flashEnabled: Boolean = false,
    val canSwitchCamera: Boolean = false,
    val isFrontCamera: Boolean = false,
    val zoomRatio: Float = 1f,
    val supportedZoomRatios: List<Float> = emptyList(),
)

/** A screen-owned CameraX session. Camera hardware stays outside the ViewModel. */
internal class CameraSession(context: Context) {
    private val executor = ContextCompat.getMainExecutor(context)
    private val controller = LifecycleCameraController(context.applicationContext).apply {
        setEnabledUseCases(CameraController.IMAGE_CAPTURE)
        imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        isPinchToZoomEnabled = false
    }
    var controls by mutableStateOf(CameraControls())
        private set
    var capturedPreview by mutableStateOf<Bitmap?>(null)
        private set
    private var preview: PreviewView? = null
    private var lifecycleOwner: LifecycleOwner? = null
    private var removeObservers: () -> Unit = {}
    private var generation = 0

    fun attach(view: PreviewView, owner: LifecycleOwner) {
        detach()
        preview = view
        lifecycleOwner = owner
        val attachment = generation
        val initialization = controller.initializationFuture
        initialization.addListener({
            // An old callback must not reattach a camera that has already been detached.
            if (attachment != generation) return@addListener
            try {
                initialization.get()
                if (!selectAvailableCamera()) return@addListener
                view.controller = controller
                controller.bindToLifecycle(owner)
                updateFlash()
                observeCamera(view, owner)
            } catch (_: Exception) {
                controls = controls.copy(status = CameraPreviewStatus.ERROR)
            }
        }, executor)
    }

    private fun selectAvailableCamera(): Boolean {
        val hasBack = controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        val hasFront = controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        if (!hasBack && !hasFront) {
            controls = controls.copy(status = CameraPreviewStatus.UNAVAILABLE)
            return false
        }
        val useFront = !hasBack || (controls.isFrontCamera && hasFront)
        controller.cameraSelector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        controls = controls.copy(isFrontCamera = useFront, canSwitchCamera = hasBack && hasFront)
        return true
    }

    private fun observeCamera(view: PreviewView, owner: LifecycleOwner) {
        val cameraState = controller.cameraInfo?.cameraState
        val zoomObserver = Observer<ZoomState> { zoom ->
            val supportedRatios = listOf(0.5f, 1f, 2f).filter { it in zoom.minZoomRatio..zoom.maxZoomRatio }
            controls = controls.copy(zoomRatio = zoom.zoomRatio, supportedZoomRatios = supportedRatios)
        }
        val streamObserver = Observer<PreviewView.StreamState> { updatePreviewStatus() }
        val cameraObserver = Observer<CameraState> { updatePreviewStatus() }
        removeObservers = {
            controller.zoomState.removeObserver(zoomObserver)
            view.previewStreamState.removeObserver(streamObserver)
            cameraState?.removeObserver(cameraObserver)
        }
        controller.zoomState.observe(owner, zoomObserver)
        view.previewStreamState.observe(owner, streamObserver)
        cameraState?.observe(owner, cameraObserver)
    }

    private fun updatePreviewStatus() {
        val cameraState = controller.cameraInfo?.cameraState?.value
        val streamState = preview?.previewStreamState?.value
        val status = when {
            cameraState?.error != null -> CameraPreviewStatus.ERROR
            cameraState?.type == CameraState.Type.OPEN && streamState == PreviewView.StreamState.STREAMING -> CameraPreviewStatus.READY
            controls.status == CameraPreviewStatus.READY -> null
            else -> controls.status
        }
        controls = controls.copy(status = status)
    }

    fun detach() {
        generation++
        removeObservers()
        removeObservers = {}
        controller.unbind()
        preview?.controller = null
        preview = null
        lifecycleOwner = null
        controls = controls.copy(status = null)
        clearCapturedPreview()
    }

    fun toggleFlash() {
        if (!controls.hasFlash || capturedPreview != null) return
        controls = controls.copy(flashEnabled = !controls.flashEnabled)
        updateFlash()
    }

    private fun updateFlash() {
        val hasFlash = controller.cameraInfo?.hasFlashUnit() == true
        controls = controls.copy(hasFlash = hasFlash, flashEnabled = hasFlash && controls.flashEnabled)
        controller.imageCaptureFlashMode = if (controls.flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    fun switchCamera() {
        if (!controls.canSwitchCamera || capturedPreview != null) return
        val view = preview ?: return
        val owner = lifecycleOwner ?: return
        controls = controls.copy(isFrontCamera = !controls.isFrontCamera, flashEnabled = false, zoomRatio = 1f)
        attach(view, owner)
    }

    fun selectZoom(ratio: Float) {
        if (ratio !in controls.supportedZoomRatios || capturedPreview != null) return
        val future = controller.setZoomRatio(ratio)
        future.addListener({ runCatching { future.get() } }, executor)
    }

    fun capture(file: File, onSuccess: (File) -> Unit, onError: () -> Unit) {
        val metadata = ImageCapture.Metadata().apply { isReversedHorizontal = controls.isFrontCamera }
        val options = ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build()
        try {
            // Freeze exactly what was visible at the shutter; keep the full JPEG as the draft.
            capturedPreview = preview?.bitmap
            controller.takePicture(options, executor, object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onSuccess(file)
                override fun onError(exception: ImageCaptureException) {
                    clearCapturedPreview()
                    onError()
                }
            })
        } catch (_: Exception) {
            clearCapturedPreview()
            onError()
        }
    }

    fun clearCapturedPreview() {
        capturedPreview = null
    }
}

@Composable
internal fun CameraViewfinder(session: CameraSession) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val background = MaterialTheme.colorScheme.surfaceContainerLow
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { view }, modifier = Modifier.fillMaxSize(),
            update = { it.setBackgroundColor(background.toArgb()) })
        // Cover the PreviewView surface while CameraX opens or switches lenses.
        if (session.controls.status != CameraPreviewStatus.READY) {
            Box(Modifier.fillMaxSize().background(background).testTag("camera_placeholder"))
        }
        session.capturedPreview?.let { bitmap ->
            Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize().testTag("camera_captured_preview"),
                contentScale = ContentScale.Crop)
        }
    }
    DisposableEffect(session, owner, view) {
        session.attach(view, owner)
        onDispose { session.detach() }
    }
}
