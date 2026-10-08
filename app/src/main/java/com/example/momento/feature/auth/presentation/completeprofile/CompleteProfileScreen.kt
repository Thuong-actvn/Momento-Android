package com.example.momento.feature.auth.presentation.completeprofile

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.momento.R
import com.example.momento.ui.theme.MomentoTheme
import java.util.Locale

enum class UsernameAvailability { Unchecked, Available, Unavailable }

private val ProfileBackground = Color(0xFFFDF9F4)
private val ProfileText = Color(0xFF1C1C19)
private val ProfileSupportingText = Color(0xFF57423D)
private val ProfileMuted = Color(0xFF8A726C)
private val ProfileAvailableBackground = Color(0xFFB9ECC6)
private val ProfileAvailableText = Color(0xFF3F6C4E)
private val ProfileDisabledBackground = Color(0xFFE6E2DD)
private val UsernamePattern = Regex("^[a-z0-9_]{3,20}$")

@Composable
fun CompleteProfileScreen(
    onProfileCompleted: () -> Unit,
    onBack: () -> Unit = {},
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    CompleteProfileContent(
        displayName = displayName,
        username = username,
        // A valid format does not establish uniqueness. Supply a checked status here
        // when the backend exposes a username availability check.
        usernameAvailability = UsernameAvailability.Unchecked,
        onDisplayNameChange = { displayName = it.take(50) },
        onUsernameChange = { username = it.lowercase(Locale.ROOT).take(20) },
        onBack = onBack,
        onComplete = {
            keyboard?.hide()
            onProfileCompleted()
        },
    )
}

/** Stateless form, ready to receive username availability from the profile flow. */
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
) {
    val colors = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val usernameFormatInvalid = username.isNotEmpty() && !UsernamePattern.matches(username)
    val canComplete = displayName.trim().isNotEmpty() && displayName.trim().length <= 50 &&
        UsernamePattern.matches(username) && usernameAvailability != UsernameAvailability.Unavailable
    val complete = { if (canComplete) onComplete() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ProfileBackground)
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
                    color = ProfileText,
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = stringResource(R.string.complete_profile_description),
                    color = ProfileSupportingText,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 23.sp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = stringResource(R.string.complete_profile_display_name)
                Text(text = label, color = ProfileText, style = MaterialTheme.typography.bodySmall)
                ProfileTextField(
                    value = displayName,
                    onValueChange = onDisplayNameChange,
                    label = label,
                    placeholder = stringResource(R.string.complete_profile_display_name_placeholder),
                    iconRes = R.drawable.ic_profile_display_name,
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
                    Text(text = label, color = ProfileText, style = MaterialTheme.typography.bodySmall)
                    if (!usernameFormatInvalid && username.isNotEmpty() &&
                        usernameAvailability != UsernameAvailability.Unchecked
                    ) {
                        UsernameAvailabilityBadge(usernameAvailability)
                    }
                }
                ProfileTextField(
                    value = username,
                    onValueChange = onUsernameChange,
                    label = label,
                    placeholder = stringResource(R.string.complete_profile_username_placeholder),
                    iconRes = R.drawable.ic_profile_username,
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
            }

            Button(
                onClick = complete,
                enabled = canComplete,
                modifier = Modifier.padding(top = 20.dp).fillMaxWidth().heightIn(min = 52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = ProfileDisabledBackground.copy(alpha = 0.7f),
                    disabledContentColor = ProfileMuted.copy(alpha = 0.7f),
                ),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
                elevation = null,
            ) {
                Text(
                    text = stringResource(R.string.complete_profile_finish),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_auth_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompleteProfileTopBar(onBack: () -> Unit) {
    Surface(color = ProfileBackground, shadowElevation = 1.dp) {
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
                containerColor = ProfileBackground,
                titleContentColor = ProfileText,
                navigationIconContentColor = ProfileText,
            ),
        )
    }
}

@Composable
private fun ProfileTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    @DrawableRes iconRes: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    iconTint: Color = ProfileMuted,
) {
    val inputStyle = MaterialTheme.typography.bodyMedium

    Surface(shape = CircleShape, color = Color.White, shadowElevation = 1.dp) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = inputStyle,
            placeholder = {
                Text(
                    text = placeholder,
                    style = inputStyle,
                )
            },
            prefix = {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                        .offset(
                            y = if (iconRes == R.drawable.ic_profile_username) 2.dp else 0.dp,
                        ),
                )
            },
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = ProfileText,
                unfocusedTextColor = ProfileText,
                focusedPlaceholderColor = ProfileMuted,
                unfocusedPlaceholderColor = ProfileMuted,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

@Composable
private fun UsernameAvailabilityBadge(availability: UsernameAvailability) {
    val available = availability == UsernameAvailability.Available
    val colors = MaterialTheme.colorScheme
    val contentColor = if (available) ProfileAvailableText else colors.onErrorContainer

    Row(
        modifier = Modifier
            .background(if (available) ProfileAvailableBackground else colors.errorContainer, CircleShape)
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
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
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
        CompleteProfileScreen(onProfileCompleted = {}, onBack = {})
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
