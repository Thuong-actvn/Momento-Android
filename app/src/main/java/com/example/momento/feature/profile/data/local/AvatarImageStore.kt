package com.example.momento.feature.profile.data.local

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PreparedAvatar(val file: File, val mimeType: String)

class InvalidAvatarException(val tooLarge: Boolean = false) : IOException()

/** Owns temporary images so drafts survive rotation without keeping library URI permissions. */
@Singleton
class AvatarImageStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val drafts get() = File(context.cacheDir, "avatar_drafts").apply { mkdirs() }
    private val camera get() = File(context.cacheDir, "avatar_camera").apply { mkdirs() }

    fun createCameraUri(): Uri {
        val file = File.createTempFile("camera_", ".jpg", camera)
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    suspend fun prepare(uri: Uri): PreparedAvatar = withContext(Dispatchers.IO) {
        val file = File.createTempFile("avatar_", ".image", drafts)
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw InvalidAvatarException()
            input.use { source ->
                file.outputStream().use { target ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        ensureActive()
                        val count = source.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 5L * 1024 * 1024) throw InvalidAvatarException(tooLarge = true)
                        target.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val mimeType = bounds.outMimeType
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                mimeType !in setOf("image/jpeg", "image/png", "image/webp")
            ) throw InvalidAvatarException()
            PreparedAvatar(file, mimeType)
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }

    fun deleteDraft(path: String?) {
        if (path == null) return
        val file = File(path)
        if (file.parentFile?.canonicalFile == drafts.canonicalFile) file.delete()
    }

    fun deleteCamera(uri: Uri?) {
        if (uri?.authority != "${context.packageName}.fileprovider") return
        val name = uri.lastPathSegment ?: return
        val file = File(camera, name)
        if (file.parentFile?.canonicalFile == camera.canonicalFile) file.delete()
    }
}
