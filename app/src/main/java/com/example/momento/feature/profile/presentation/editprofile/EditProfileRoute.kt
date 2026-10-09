package com.example.momento.feature.profile.presentation.editprofile

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::chooseAvatar)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::onCameraResult)

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
        },
        onRetry = viewModel::refreshProfile,
        modifier = modifier,
    )
}
