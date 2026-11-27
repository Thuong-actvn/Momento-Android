package com.example.momento.feature.moment.data.local

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import android.util.Log
import androidx.core.util.tryWrite
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.roundToInt

@Singleton
class JournalPreviewStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val root = File(context.noBackupFilesDir, "journal_previews")

    suspend fun loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(1080, 1080)
            .scale(Scale.FIT)
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        val loader = SingletonImageLoader.get(context)
        val result = withTimeoutOrNull(10_000) { loader.execute(request) }
        if (result is SuccessResult) result.image.toBitmap() else null
    }

    suspend fun save(accountId: String, photoId: String, bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            val path = relativePath(accountId, photoId)
            val file = File(root, path)
            val longestEdge = maxOf(bitmap.width, bitmap.height)
            val image = if (longestEdge > 1080) {
                val scale = 1080f / longestEdge
                val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
                val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            } else {
                bitmap
            }
            try {
                file.parentFile?.mkdirs()
                AtomicFile(file).tryWrite { output ->
                    if (!image.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                        throw IOException("Unable to encode Journal preview")
                    }
                }
                if (file.isFile && file.length() > 0L) path else null
            } catch (error: IOException) {
                Log.w("MomentoJournal", "Unable to write Journal preview", error)
                null
            } finally {
                if (image !== bitmap) image.recycle()
            }
        }

    suspend fun find(accountId: String, photoId: String, path: String?): File? {
        if (path == null || path != relativePath(accountId, photoId)) return null
        return withContext(Dispatchers.IO) { File(root, path).takeIf { it.isFile && it.length() > 0 } }
    }

    suspend fun deletePhotos(accountId: String, photoIds: Collection<String>) = withContext(Dispatchers.IO) {
        for (photoId in photoIds) {
            AtomicFile(File(root, relativePath(accountId, photoId))).delete()
        }
    }

    suspend fun deleteAccount(accountId: String) {
        withContext(Dispatchers.IO) {
            val directory = File(root, fileKey(accountId))
            if (!directory.deleteRecursively()) {
                Log.w("MomentoJournal", "Unable to remove all Journal previews")
            }
        }
    }

    private fun relativePath(accountId: String, photoId: String): String =
        "${fileKey(accountId)}/${fileKey(photoId)}.jpg"

    private fun fileKey(id: String): String = id.encodeUtf8().sha256().hex()
}
