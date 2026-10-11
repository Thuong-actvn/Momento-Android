package com.example.momento.feature.profile.presentation.editprofile

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::chooseAvatar)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::onCameraResult)

    fun takeAvatarPhoto() {
        viewModel.createCameraUri()?.let { uri ->
            try {
                camera.launch(uri)
            } catch (_: ActivityNotFoundException) {
                viewModel.onCameraResult(false)
                viewModel.reportImageSourceError()
            } catch (_: SecurityException) {
                viewModel.onCameraResult(false)
                viewModel.reportImageSourceError()
            }
        }
    }

    // Declaring CAMERA for the in-app camera also requires permission before ACTION_IMAGE_CAPTURE.
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takeAvatarPhoto() else viewModel.reportImageSourceError()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onBack()
    }
    BackHandler { if (!state.isBusy) onBack() }

    EditProfileScreen(
        state = state, onBack = onBack, onSave = viewModel::save,
        onDisplayNameChanged = viewModel::onDisplayNameChanged,
        onBirthdayChanged = viewModel::onBirthdayChanged,
        onGallery = {
            try {
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            } catch (_: ActivityNotFoundException) {
                viewModel.reportImageSourceError()
            } catch (_: SecurityException) {
                viewModel.reportImageSourceError()
            }
        },
        onCamera = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                takeAvatarPhoto()
            else cameraPermission.launch(Manifest.permission.CAMERA)
        },
        onRetry = viewModel::refreshProfile,
        modifier = modifier,
    )
}
