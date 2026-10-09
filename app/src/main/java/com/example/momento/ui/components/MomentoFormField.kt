package com.example.momento.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

/** A labeled card for an editable value, a read-only value, or a picker. */
@Composable
fun MomentoFormField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: ((String) -> Unit)? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    trailingIcon: ImageVector? = null,
    isTrailingIconActive: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        MomentoCard {
            Column(Modifier.heightIn(min = 78.dp).padding(horizontal = 16.dp, vertical = 14.dp)) {
                Text(label, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onValueChange != null) {
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            enabled = enabled,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                            cursorBrush = SolidColor(colors.primary),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.weight(1f).semantics { contentDescription = label },
                        )
                    } else {
                        Text(
                            value, modifier = Modifier.weight(1f), color = colors.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    trailingIcon?.let {
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val clickModifier = if (onTrailingIconClick == null) Modifier else
                            Modifier.clickable(
                                enabled = enabled,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button,
                                onClick = onTrailingIconClick,
                            )
                        Box(
                            modifier = Modifier.size(24.dp).then(clickModifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = it,
                                contentDescription = if (onTrailingIconClick != null) label else null,
                                tint = if (enabled && (isPressed || isTrailingIconActive)) colors.primary
                                    else colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        error?.let {
            Text(it, color = colors.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}
