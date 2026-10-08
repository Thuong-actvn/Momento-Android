package com.example.momento.feature.auth.presentation.completeprofile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R
import com.example.momento.ui.components.MomentoButton
import com.example.momento.ui.components.MomentoTextField
import com.example.momento.ui.theme.MomentoTheme

@Composable
fun CompleteProfileScreen(
    onProfileCompleted: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: CompleteProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) onProfileCompleted()
    }

    CompleteProfileContent(
        displayName = state.displayName,
        username = state.username,
        usernameAvailability = state.usernameAvailability,
        onDisplayNameChange = viewModel::onDisplayNameChange,
        onUsernameChange = viewModel::onUsernameChange,
        onBack = onBack,
        isSubmitting = state.isSubmitting,
        error = state.error,
        usernameCheckError = state.usernameCheckError,
        onRetryUsernameCheck = viewModel::retryUsernameCheck,
        onComplete = {
            keyboard?.hide()
            viewModel.completeProfile()
        },
    )
}

/** Stateless form used by the screen and previews. */
@Composable
fun CompleteProfileContent(
    displayName: String,
    username: String,
    usernameAvailability: UsernameAvailability,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onBack: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    isSubmitting: Boolean = false,
    error: String? = null,
    usernameCheckError: String? = null,
    onRetryUsernameCheck: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val usernameFormatInvalid = username.isNotEmpty() && !UsernamePattern.matches(username)
    val canComplete = CompleteProfileUiState(displayName = displayName, username = username,
        usernameAvailability = usernameAvailability, isSubmitting = isSubmitting).canSubmit
    val complete = { if (canComplete) onComplete() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding(),
    ) {
        CompleteProfileTopBar(onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.complete_profile_heading),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(R.string.complete_profile_description),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.complete_profile_display_name)
                Text(text = label, color = colors.onSurface, style = MaterialTheme.typography.bodySmall)
                MomentoTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    label = label,
                    placeholder = stringResource(R.string.complete_profile_display_name_placeholder),
                    enabled = !isSubmitting,
                    prefix = {
                        Icon(
                            painter = painterResource(R.drawable.ic_profile_display_name),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    ),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.complete_profile_username)
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = label, color = colors.onSurface, style = MaterialTheme.typography.bodySmall)
                    if (!usernameFormatInvalid && username.isNotEmpty() &&
                        (usernameAvailability == UsernameAvailability.Available ||
                            usernameAvailability == UsernameAvailability.Unavailable)
                    ) {
                        UsernameAvailabilityBadge(usernameAvailability)
                    }
                }
                MomentoTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = label,
                    placeholder = stringResource(R.string.complete_profile_username_placeholder),
                    enabled = !isSubmitting,
                    isError = usernameFormatInvalid || usernameAvailability == UsernameAvailability.Unavailable,
                    prefix = {
                        Icon(
                            painter = painterResource(R.drawable.ic_profile_username),
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(16.dp).offset(y = 2.dp),
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { complete() }),
                )
                if (usernameFormatInvalid) {
                    Text(
                        text = stringResource(R.string.complete_profile_username_format),
                        color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (usernameCheckError != null) {
                    Text(text = usernameCheckError, color = colors.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    TextButton(onClick = onRetryUsernameCheck, enabled = !isSubmitting) {
                        Text(stringResource(R.string.complete_profile_retry_check))
                    }
                }
            }

            if (error != null) {
                Text(text = error, color = colors.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            MomentoButton(
                text = stringResource(R.string.complete_profile_finish),
                onClick = complete,
                enabled = canComplete,
                isLoading = isSubmitting,
                modifier = Modifier.padding(top = 20.dp),
                trailingIcon = R.drawable.ic_auth_arrow_forward,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteProfileTopBar(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.background) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.complete_profile_title),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.ic_profile_arrow_back),
                        contentDescription = stringResource(R.string.complete_profile_back),
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.background,
                titleContentColor = colors.onSurface,
                navigationIconContentColor = colors.onSurface,
            ),
        )
    }
}

@Composable
private fun UsernameAvailabilityBadge(availability: UsernameAvailability) {
    val available = availability == UsernameAvailability.Available
    val colors = MaterialTheme.colorScheme
    val contentColor = if (available) colors.onSecondaryContainer else colors.onErrorContainer

    Row(
        modifier = Modifier
            .background(if (available) colors.secondaryContainer else colors.errorContainer, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(
                if (available) R.drawable.ic_profile_available else R.drawable.ic_profile_unavailable,
            ),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(
                if (available) R.string.complete_profile_username_available
                else R.string.complete_profile_username_unavailable,
            ),
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Preview(name = "Complete Profile - Available", widthDp = 390, heightDp = 478, locale = "vi")
@Composable
private fun CompleteProfileAvailablePreview() {
    CompleteProfilePreview(UsernameAvailability.Available)
}

@Preview(name = "Complete Profile - Unavailable", widthDp = 390, heightDp = 478, locale = "vi")
@Composable
private fun CompleteProfileUnavailablePreview() {
    CompleteProfilePreview(UsernameAvailability.Unavailable)
}

@Preview(name = "Complete Profile - Empty", widthDp = 390, heightDp = 844, locale = "vi")
@Composable
private fun CompleteProfileEmptyPreview() {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        CompleteProfileContent(displayName = "", username = "",
            usernameAvailability = UsernameAvailability.Unchecked,
            onDisplayNameChange = {}, onUsernameChange = {}, onBack = {}, onComplete = {})
    }
}

@Composable
private fun CompleteProfilePreview(availability: UsernameAvailability) {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        CompleteProfileContent(
            displayName = "Minh Anh",
            username = "minhanh",
            usernameAvailability = availability,
            onDisplayNameChange = {},
            onUsernameChange = {},
            onBack = {},
            onComplete = {},
        )
    }
}
