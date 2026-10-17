package com.example.momento.feature.widget.presentation.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.util.AtomicFile
import androidx.core.content.FileProvider
import androidx.core.util.tryWrite
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Scale
import coil3.toBitmap
import coil3.transform.CircleCropTransformation
import coil3.transform.RoundedCornersTransformation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.encodeUtf8

class WidgetImageLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val authority = "${context.packageName}.fileprovider"
    private val imageDirectory = File(context.filesDir, "widget_images")

    suspend fun load(
        url: String?,
        filePrefix: String,
        sizeDp: Int,
    ): Uri? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank()) {
            return@withContext null
        }

        val density = context.resources.displayMetrics.density
        val sizePx = (sizeDp * density).roundToInt().coerceAtLeast(1)
        val transformation = if (filePrefix == "avatar") {
            CircleCropTransformation()
        } else {
            RoundedCornersTransformation(PHOTO_CORNER_RADIUS_DP * density)
        }
        val imageKey = "$url|$sizePx|${transformation.cacheKey}"
        val fileKey = imageKey.encodeUtf8().sha256().hex()
        val imageFile = File(imageDirectory, "$filePrefix-$fileKey.png")
        if (imageFile.isFile) {
            imageFile.setLastModified(System.currentTimeMillis())
            return@withContext FileProvider.getUriForFile(context, authority, imageFile)
        }

        val imageLoader = SingletonImageLoader.get(context)
        val result = withTimeoutOrNull(IMAGE_TIMEOUT_MILLIS) {
            // Coil crops and rounds the image; use the same result on every Android version.
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(sizePx, sizePx)
                .scale(Scale.FILL)
                .transformations(transformation)
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.DISABLED)
                .build()
            imageLoader.execute(request)
        }
        if (result !is SuccessResult) {
            return@withContext null
        }

        imageDirectory.mkdirs()
        val bitmap = result.image.toBitmap()
        AtomicFile(imageFile).tryWrite { output ->
            // PNG preserves the transparent corners produced by Coil.
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Unable to save widget image"
            }
        }
        FileProvider.getUriForFile(context, authority, imageFile)
    }

    fun removeOldImages(photoUri: Uri?, avatarUri: Uri?) {
        val currentFileNames = setOfNotNull(photoUri?.lastPathSegment, avatarUri?.lastPathSegment)
        val oldImages = imageDirectory.listFiles().orEmpty()
            .filter { file -> file.name !in currentFileNames }
            .sortedByDescending { file -> file.lastModified() }
            // Keep the previous pair while the launcher applies the new RemoteViews.
            .drop(PREVIOUS_IMAGE_COUNT)

        for (image in oldImages) {
            image.delete()
        }
    }

    fun grantLauncherAccess(photoUri: Uri?, avatarUri: Uri?) {
        val imageUris = listOfNotNull(photoUri, avatarUri)
        if (imageUris.isEmpty()) {
            return
        }

        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homeActivity = checkNotNull(
            context.packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY),
        ) { "Home screen launcher not found" }
        val launcherPackage = homeActivity.activityInfo.packageName

        for (uri in imageUris) {
            context.grantUriPermission(launcherPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun clear() {
        val widgetImagesUri = Uri.parse("content://$authority/widget_images")
        context.revokeUriPermission(widgetImagesUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        for (image in imageDirectory.listFiles().orEmpty()) {
            image.delete()
        }
    }

    private companion object {
        const val IMAGE_TIMEOUT_MILLIS = 15_000L
        const val PREVIOUS_IMAGE_COUNT = 2
        const val PHOTO_CORNER_RADIUS_DP = 16f
    }
}
