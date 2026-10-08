package com.example.momento.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun MomentoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    isError: Boolean = false,
    prefix: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MaterialTheme.colorScheme
    val inputStyle = MaterialTheme.typography.bodyMedium
    val shape = MaterialTheme.shapes.large
    val containerColor = colors.surfaceContainerLowest
    val placeholderColor = colors.onSurfaceVariant.copy(alpha = 0.65f)
    val prefixContent: (@Composable () -> Unit)? = if (prefix == null) null else {
        {
            Box(modifier = Modifier.padding(end = 8.dp), contentAlignment = Alignment.Center) {
                prefix()
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
        shadowElevation = 1.dp,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            isError = isError,
            singleLine = true,
            textStyle = inputStyle,
            placeholder = { Text(text = placeholder, style = inputStyle) },
            prefix = prefixContent,
            shape = shape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                disabledContainerColor = containerColor,
                errorContainerColor = containerColor,
                focusedTextColor = colors.onSurface,
                unfocusedTextColor = colors.onSurface,
                disabledTextColor = colors.onSurface.copy(alpha = 0.65f),
                errorTextColor = colors.onSurface,
                focusedPlaceholderColor = placeholderColor,
                unfocusedPlaceholderColor = placeholderColor,
                disabledPlaceholderColor = placeholderColor,
                errorPlaceholderColor = placeholderColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
            ),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        )
    }
}
