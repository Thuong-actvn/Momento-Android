package com.example.momento.feature.auth.presentation.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R
import com.example.momento.ui.theme.MomentoTheme

@Composable
fun LoginScreen(
    onProfileRequired: () -> Unit,
    onAuthSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.user) {
        state.user?.let { user ->
            keyboard?.hide()
            if (user.isProfileCompleted) onAuthSuccess() else onProfileRequired()
        }
    }

    LoginContent(
        state = state,
        canUseActivity = activity != null,
        onPhoneNumberChange = viewModel::onPhoneNumberChange,
        onOtpChange = viewModel::onOtpChange,
        onPrimaryAction = {
            activity?.let {
                keyboard?.hide()
                viewModel.onPrimaryAction(it)
            }
        },
        onResendCode = {
            activity?.let {
                keyboard?.hide()
                viewModel.sendCode(it)
            }
        },
        onGoogleSignIn = {
            activity?.let {
                keyboard?.hide()
                viewModel.signInWithGoogle(it)
            }
        },
    )
}

@Composable
private fun LoginContent(
    state: LoginUiState,
    canUseActivity: Boolean,
    onPhoneNumberChange: (String) -> Unit,
    onOtpChange: (String) -> Unit,
    onPrimaryAction: () -> Unit,
    onResendCode: () -> Unit,
    onGoogleSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val canScroll = state.isOtpStep || state.errorMessage.isNotBlank() || isKeyboardOpen
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .then(if (canScroll) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        LoginBrandHeader()
        Spacer(Modifier.height(16.dp))
        PhoneNumberField(
            value = state.phoneNumber,
            onValueChange = onPhoneNumberChange,
            enabled = state.canEditPhoneNumber && canUseActivity,
            onContinue = onPrimaryAction,
        )

        if (state.isOtpStep) {
            Spacer(Modifier.height(20.dp))
            OtpVerification(
                otp = state.otp,
                onOtpChange = onOtpChange,
                enabled = state.canEditOtp,
                onVerifyCode = onPrimaryAction,
            )
            OtpStatusRow(
                errorMessage = state.errorMessage,
                canResendCode = state.canSendCode && canUseActivity,
                onResendCode = onResendCode,
            )
        } else if (state.errorMessage.isNotBlank()) {
            Text(
                text = state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (!canScroll) Spacer(Modifier.weight(1f))
        LoginActions(state, canUseActivity, onPrimaryAction, onGoogleSignIn)
    }
}

@Composable
private fun LoginActions(
    state: LoginUiState,
    canUseActivity: Boolean,
    onPrimaryAction: () -> Unit,
    onGoogleSignIn: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(modifier = Modifier.padding(top = 16.dp)) {
        Button(
            onClick = onPrimaryAction,
            enabled = state.primaryActionEnabled && canUseActivity,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
        ) {
            if (state.isPrimaryActionLoading) {
                LoginProgressIndicator()
            } else {
                Text(
                    text = stringResource(
                        if (state.isOtpStep) R.string.verify_otp else R.string.continue_after_login,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    painter = painterResource(R.drawable.ic_auth_arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
            Text(
                text = stringResource(R.string.login_or),
                modifier = Modifier.padding(horizontal = 16.dp),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider(Modifier.weight(1f), color = colors.outlineVariant)
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onGoogleSignIn,
            enabled = state.canSignIn && canUseActivity,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surfaceContainerLowest,
                contentColor = colors.onSurface,
                disabledContainerColor = colors.surfaceContainerLowest,
                disabledContentColor = colors.onSurface.copy(alpha = 0.65f),
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, disabledElevation = 1.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (state.isGoogleSignInInProgress) {
                LoginProgressIndicator()
            } else {
                Image(
                    painter = painterResource(R.drawable.ic_auth_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sign_in_with_google),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        LoginLegalNotice()
    }
}

@Composable
private fun LoginBrandHeader() {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo_app),
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(CircleShape),
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_tagline),
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 23.sp),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PhoneNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onContinue: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.phone_number_label)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = colors.onSurfaceVariant,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
            placeholder = { Text(stringResource(R.string.login_phone_placeholder)) },
            leadingIcon = {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_auth_vietnam),
                        contentDescription = null,
                        modifier = Modifier.size(width = 20.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)),
                    )
                    Text(
                        text = stringResource(R.string.login_country_code),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    VerticalDivider(
                        modifier = Modifier.padding(start = 4.dp).height(12.dp).clip(CircleShape),
                        thickness = 4.dp,
                        color = colors.outline.copy(alpha = 0.6f),
                    )
                }
            },
            shape = MaterialTheme.shapes.small,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                disabledContainerColor = colors.surfaceContainerLow,
                disabledTextColor = colors.onSurface,
                focusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                unfocusedPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                disabledPlaceholderColor = colors.onSurfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onContinue() }),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}

@Composable
private fun OtpVerification(
    otp: String,
    onOtpChange: (String) -> Unit,
    enabled: Boolean,
    onVerifyCode: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.login_otp_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = otp,
            onValueChange = onOtpChange,
            label = { Text(stringResource(R.string.otp_code_label)) },
            enabled = enabled,
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onVerifyCode() }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun OtpStatusRow(
    errorMessage: String,
    canResendCode: Boolean,
    onResendCode: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = errorMessage,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.error,
            style = MaterialTheme.typography.bodySmall,
        )

        Text(
            text = stringResource(R.string.login_resend_otp),
            modifier = Modifier
                .clickable(enabled = canResendCode, role = Role.Button, onClick = onResendCode)
                .padding(vertical = 4.dp),
            color = if (canResendCode) colors.primary else colors.onSurface.copy(alpha = 0.38f),
            style = MaterialTheme.typography.bodySmall,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun LoginProgressIndicator() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        color = LocalContentColor.current,
        strokeWidth = 2.dp,
    )
}

@Composable
private fun LoginLegalNotice() {
    val linkStyle = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
    )
    val notice = buildAnnotatedString {
        append(stringResource(R.string.login_legal_prefix))
        withStyle(linkStyle) { append(stringResource(R.string.login_terms)) }
        append(stringResource(R.string.login_legal_and))
        withStyle(linkStyle) { append(stringResource(R.string.login_privacy_policy)) }
        append(stringResource(R.string.login_legal_suffix, stringResource(R.string.app_name)))
    }

    Text(
        text = notice,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.5.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        textAlign = TextAlign.Center,
    )
}

@Preview(name = "Sign in", widthDp = 390, heightDp = 809, showBackground = true, locale = "vi")
@Preview(name = "Sign in - compact", widthDp = 320, heightDp = 640, showBackground = true, locale = "vi")
@Composable
private fun LoginScreenPreview() {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        LoginContent(
            state = LoginUiState(isBusy = false, message = ""),
            canUseActivity = true,
            onPhoneNumberChange = {},
            onOtpChange = {},
            onPrimaryAction = {},
            onResendCode = {},
            onGoogleSignIn = {},
        )
    }
}

@Preview(name = "Sign in - OTP", widthDp = 390, heightDp = 809, showBackground = true, locale = "vi")
@Composable
private fun LoginOtpPreview() {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        LoginContent(
            state = LoginUiState(
                isBusy = false, otpRequested = true, codeSent = true,
                phoneNumber = "0988123456", message = "",
            ),
            canUseActivity = true,
            onPhoneNumberChange = {},
            onOtpChange = {},
            onPrimaryAction = {},
            onResendCode = {},
            onGoogleSignIn = {},
        )
    }
}

@Preview(name = "OTP error - compact", widthDp = 320, showBackground = true, locale = "vi")
@Composable
private fun OtpStatusRowPreview() {
    MomentoTheme(darkTheme = false, dynamicColor = false) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
        ) {
            OtpStatusRow(
                errorMessage = "Mã OTP không chính xác hoặc đã hết hạn. Vui lòng kiểm tra mã hoặc yêu cầu gửi lại.",
                canResendCode = true,
                onResendCode = {},
            )
        }
    }
}
