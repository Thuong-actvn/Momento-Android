package com.example.momento.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MomentoCard(
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    shape: Shape = MaterialTheme.shapes.small,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surfaceContainerLow,
        border = if (outlined) BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)) else null,
        content = content,
    )
}

@Composable
fun MomentoSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    supportingText: String? = null,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    FlowRow(
        modifier = modifier.fillMaxWidth().semantics { heading() },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        count?.let {
            Surface(
                shape = CircleShape,
                color = if (highlighted) colors.primaryContainer else colors.surfaceContainer,
                contentColor = if (highlighted) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            ) {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                )
            }
        }
        supportingText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}

/** A title-only row is compact; a subtitle adds padding and a larger icon. */
@Composable
fun MomentoMenuRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badge: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBackground: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.025f),
    chevronColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    highlightOnPress: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val hasSubtitle = subtitle != null
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlightContent = highlightOnPress && enabled && isPressed

    val clickModifier = when {
        onClick == null -> Modifier

        highlightOnPress -> Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )

        else -> Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = if (hasSubtitle) 78.dp else 62.dp)
            .padding(if (hasSubtitle) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(if (hasSubtitle) 44.dp else 36.dp),
            shape = CircleShape,
            color = iconBackground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = iconColor,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f, fill = badge == null),
                    color = if (highlightContent) colors.primary else colors.onSurface,
                    style = if (hasSubtitle) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                badge?.let {
                    Surface(color = colors.secondaryContainer, shape = CircleShape) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            color = colors.onSecondaryContainer,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            subtitle?.let {
                Text(
                    text = it,
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (highlightContent) colors.primary else chevronColor,
        )
    }
}
