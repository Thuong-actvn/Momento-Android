package com.example.momento.feature.profile.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.momento.R
import com.example.momento.ui.components.MomentoAvatar
import com.example.momento.ui.components.MomentoMenuRow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
internal fun EditProfileAvatar(
    image: String?,
    onChange: () -> Unit,
    enabled: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = stringResource(R.string.profile_change_avatar)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp)) {
            Surface(
                onClick = onChange,
                enabled = enabled,
                shape = CircleShape,
                border = BorderStroke(4.dp, colors.onSurface.copy(alpha = 0.06f)),
                modifier = Modifier.size(112.dp),
            ) {
                MomentoAvatar(image, description, Modifier.size(112.dp))
            }
            Surface(
                shape = CircleShape, color = colors.primary, contentColor = colors.onPrimary,
                shadowElevation = 2.dp, modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                IconButton(onClick = onChange, enabled = enabled, modifier = Modifier.size(40.dp)) {
                    if (isPreparing) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.PhotoCamera, contentDescription = description, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onChange, enabled = enabled) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(description, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AvatarSourceSheet(onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.profile_change_avatar), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            MomentoMenuRow(
                title = stringResource(R.string.profile_avatar_gallery), icon = Icons.Outlined.PhotoLibrary,
                onClick = onGallery, iconColor = MaterialTheme.colorScheme.primary,
            )
            MomentoMenuRow(
                title = stringResource(R.string.profile_avatar_camera), icon = Icons.Outlined.PhotoCamera,
                onClick = onCamera, iconColor = MaterialTheme.colorScheme.primary,
            )
            Text(stringResource(R.string.profile_avatar_formats), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
internal fun ProfileSaveButton(onClick: () -> Unit, enabled: Boolean, isSaving: Boolean) {
    Button(
        onClick = onClick, enabled = enabled, shape = CircleShape,
        modifier = Modifier.padding(end = 12.dp).height(40.dp),
    ) {
        if (isSaving) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(stringResource(R.string.profile_save), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdayPicker(value: LocalDate?, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val selectableDates = remember(today) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
            override fun isSelectableYear(year: Int): Boolean = year <= today.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        initialDisplayedMonthMillis = (value ?: today.minusYears(18)).atStartOfDay(ZoneOffset.UTC)
            .toInstant().toEpochMilli(),
        yearRange = 1900..today.year,
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let {
                    onSelect(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text(stringResource(R.string.profile_choose)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.profile_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}
