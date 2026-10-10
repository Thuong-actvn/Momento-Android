package com.example.momento.feature.moment.data.local

import android.content.Context
import android.net.Uri
import com.example.momento.core.media.ImageFileStore
import com.example.momento.core.media.InvalidImageException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Shares avatar image handling, with separate files and the photo API's 20 MiB limit. */
@Singleton
class MomentImageStore @Inject constructor(@ApplicationContext context: Context) {
    private val images = ImageFileStore(context, "moment", MAX_IMAGE_BYTES)

    /** CameraX can write directly into this file when the camera UI is implemented. */
    fun createCameraFile(): File = images.createCameraFile()

    fun createCameraUri(): Uri = images.createCameraUri()

    suspend fun prepare(uri: Uri): PreparedMomentImage = try {
        images.prepare(uri).let { PreparedMomentImage(it.file, it.mimeType) }
    } catch (error: InvalidImageException) {
        throw InvalidMomentImageException(tooLarge = error.tooLarge)
    }

    fun deleteDraft(path: String?) = images.deleteDraft(path)

    fun deleteCamera(uri: Uri?) = images.deleteCamera(uri)

    fun deleteCameraFile(file: File?) = images.deleteCameraFile(file)

    companion object {
        const val MAX_IMAGE_BYTES = 20L * 1024 * 1024
    }
}
