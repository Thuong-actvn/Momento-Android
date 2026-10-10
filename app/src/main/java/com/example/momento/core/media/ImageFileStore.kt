package com.example.momento.core.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class StoredImage(val file: File, val mimeType: String)

internal class InvalidImageException(val tooLarge: Boolean = false) : IOException()

/** Copies camera/library images into owned cache files without changing their contents. */
internal class ImageFileStore(
    private val context: Context,
    directoryPrefix: String,
    private val maxBytes: Long,
) {
    private val drafts = File(context.cacheDir, "${directoryPrefix}_drafts")
    private val camera = File(context.cacheDir, "${directoryPrefix}_camera")
    private val authority = "${context.packageName}.fileprovider"

    fun createCameraFile(): File {
        camera.mkdirs()
        return File.createTempFile("camera_", ".jpg", camera)
    }

    fun createCameraUri(): Uri {
        val file = createCameraFile()
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    suspend fun prepare(uri: Uri): StoredImage = withContext(Dispatchers.IO) {
        drafts.mkdirs()
        val file = File.createTempFile("image_", ".image", drafts)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw InvalidImageException()
            input.use { source ->
                file.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxBytes) throw InvalidImageException(tooLarge = true)
                        target.write(buffer, 0, count)
                    }
                }
            }
            ensureActive()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val mimeType = bounds.outMimeType
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                mimeType !in setOf("image/jpeg", "image/png", "image/webp")
            ) throw InvalidImageException()
            StoredImage(file, mimeType)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun deleteDraft(path: String?) {
        path?.let { deleteOwnedFile(File(it), drafts) }
    }

    fun deleteCameraFile(file: File?) {
        file?.let { deleteOwnedFile(it, camera) }
    }

    fun deleteCamera(uri: Uri?) {
        if (uri?.scheme != "content" || uri.authority != authority) return
        val segments = uri.pathSegments
        if (segments.size != 2 || segments.first() != camera.name) return
        deleteOwnedFile(File(camera, segments.last()), camera)
    }

    private fun deleteOwnedFile(file: File, directory: File) {
        if (file.parentFile?.canonicalFile == directory.canonicalFile) file.delete()
    }
}
