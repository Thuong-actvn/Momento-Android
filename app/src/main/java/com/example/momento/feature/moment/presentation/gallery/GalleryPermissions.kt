package com.example.momento.feature.moment.presentation.gallery

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

internal fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

internal fun galleryAccess(context: Context, permanentlyDenied: Boolean = false): GalleryAccess = when {
    Build.VERSION.SDK_INT >= 33 && context.hasPermission(Manifest.permission.READ_MEDIA_IMAGES) -> GalleryAccess.FULL
    Build.VERSION.SDK_INT >= 34 && context.hasPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> GalleryAccess.LIMITED
    Build.VERSION.SDK_INT <= 32 && context.hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE) -> GalleryAccess.FULL
    permanentlyDenied -> GalleryAccess.DENIED
    else -> GalleryAccess.REQUIRED
}

/** Refresh after returning from Settings or when the accessible images change. */
@Composable
internal fun ObserveGalleryChanges(onRefresh: () -> Unit) {
    val context = LocalContext.current
    val refresh by rememberUpdatedState(onRefresh)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh() }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
