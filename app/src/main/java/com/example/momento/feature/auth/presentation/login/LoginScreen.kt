package com.example.momento.feature.auth.presentation.login

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.momento.R

@Composable
fun LoginScreen(
    onProfileRequired: () -> Unit,
    onAuthSuccess: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var otp by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = { activity?.let(viewModel::signInWithGoogle) },
            enabled = !state.isBusy && state.user == null && activity != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.sign_in_with_google)) }

        HorizontalDivider()
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text(stringResource(R.string.phone_number_label)) },
            placeholder = { Text("VD: 0901234567 hoặc +84901234567") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            enabled = !state.isBusy && state.user == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { activity?.let { viewModel.sendCode(it, phoneNumber) } },
            enabled = !state.isBusy && state.user == null && phoneNumber.isNotBlank() && activity != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.send_otp)) }

        if (state.otpRequested && state.user == null) {
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it },
                label = { Text(stringResource(R.string.otp_code_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.verifyCode(otp) },
                enabled = !state.isBusy && state.codeSent && otp.length == 6,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.verify_otp)) }
        }

        if (state.isBusy) CircularProgressIndicator()
        Text(
            state.message,
            color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )

        state.user?.let { user ->
            HorizontalDivider()
            Text(stringResource(R.string.backend_user_label), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backend_username, user.username))
            Text(stringResource(R.string.backend_user_id, user.id))
            Text(
                if (user.isProfileCompleted) stringResource(R.string.profile_completed)
                else stringResource(R.string.profile_incomplete),
            )
            Button(
                onClick = if (user.isProfileCompleted) onAuthSuccess else onProfileRequired,
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.continue_after_login)) }
            TextButton(onClick = viewModel::signOut, enabled = !state.isBusy) {
                Text(stringResource(R.string.sign_out))
            }
        }
    }
}
