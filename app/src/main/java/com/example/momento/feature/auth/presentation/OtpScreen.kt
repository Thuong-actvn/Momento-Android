package com.example.momento.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R

@Composable
fun OtpScreen(
    onBack: () -> Unit,
    onProfileRequired: () -> Unit,
    onAuthSuccess: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.otp_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.auth_pending_note))
        TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_login)) }
    }
}
