package com.example.momento.feature.profile.presentation.editprofile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.core.user.domain.model.CurrentUser
import com.example.momento.feature.profile.presentation.components.AvatarSourceSheet
import com.example.momento.feature.profile.presentation.components.BirthdayPicker
import com.example.momento.feature.profile.presentation.components.EditProfileAvatar
import com.example.momento.feature.profile.presentation.components.ProfileSaveButton
import com.example.momento.ui.components.MomentoFormField
import com.example.momento.ui.components.MomentoRetryMessage
import com.example.momento.ui.components.MomentoTopBar
import com.example.momento.ui.theme.MomentoTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val birthdayFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")

@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDisplayNameChanged: (String) -> Unit,
    onBirthdayChanged: (LocalDate) -> Unit,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAvatarSources by rememberSaveable { mutableStateOf(false) }
    var showBirthdayPicker by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val canEdit = !state.isBusy && state.user != null

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                MomentoTopBar(
                    title = stringResource(R.string.profile_edit), onBack = onBack,
                    backContentDescription = stringResource(R.string.profile_back),
                    enabled = !state.isBusy, isLoading = state.isLoading,
                    actions = {
                        ProfileSaveButton(
                            onClick = { focus.clearFocus(); onSave() },
                            enabled = state.canSave, isSaving = state.isSaving,
                        )
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
                .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(32.dp))
            EditProfileAvatar(
                image = state.avatarPreview, onChange = { focus.clearFocus(); showAvatarSources = true },
                enabled = canEdit, isPreparing = state.isPreparingImage,
            )
            Spacer(Modifier.height(12.dp))
            state.error?.let { error ->
                if (state.user == null) {
                    MomentoRetryMessage(stringResource(error), stringResource(R.string.profile_retry),
                        onRetry, enabled = !state.isBusy)
                } else {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            MomentoFormField(
                label = stringResource(R.string.profile_display_name_label), value = state.displayName,
                onValueChange = onDisplayNameChanged, enabled = canEdit,
                error = state.nameError?.let { stringResource(it) },
            )
            Spacer(Modifier.height(12.dp))
            MomentoFormField(stringResource(R.string.profile_email_label),
                state.user?.email?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_value_empty))
            Spacer(Modifier.height(12.dp))
            MomentoFormField(stringResource(R.string.profile_phone_label),
                state.user?.phoneNumber?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_value_empty))
            Spacer(Modifier.height(12.dp))
            MomentoFormField(
                label = stringResource(R.string.profile_birthday_label),
                value = state.birthday?.format(birthdayFormat) ?: stringResource(R.string.profile_birthday_empty),
                trailingIcon = Icons.Outlined.CalendarMonth, enabled = canEdit,
                onTrailingIconClick = { focus.clearFocus(); showBirthdayPicker = true },
                isTrailingIconActive = showBirthdayPicker,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
    if (showAvatarSources && canEdit) {
        AvatarSourceSheet(
            onDismiss = { showAvatarSources = false },
            onGallery = { showAvatarSources = false; onGallery() },
            onCamera = { showAvatarSources = false; onCamera() },
        )
    }
    if (showBirthdayPicker && canEdit) {
        BirthdayPicker(state.birthday, onBirthdayChanged, onDismiss = { showBirthdayPicker = false })
    }
}

@Preview(showSystemUi = true)
@Composable
private fun EditProfilePreview() {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        val user = CurrentUser("preview", "johndoe", "John Doe", null,
            "john@example.com", "+1234567890", true, LocalDate.of(2000, 1, 1))
        var name by rememberSaveable { mutableStateOf(user.displayName.orEmpty()) }
        var birthday by rememberSaveable { mutableStateOf(user.birthday) }
        EditProfileScreen(
            EditProfileUiState(user = user, displayName = name, birthday = birthday),
            onBack = {}, onSave = {}, onDisplayNameChanged = { name = it },
            onBirthdayChanged = { birthday = it }, onGallery = {}, onCamera = {}, onRetry = {},
        )
    }
}
