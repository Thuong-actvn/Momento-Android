package com.example.momento.feature.profile.data.local

import android.content.Context
import android.net.Uri
import com.example.momento.core.media.ImageFileStore
import com.example.momento.core.media.InvalidImageException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

data class PreparedAvatar(val file: File, val mimeType: String)

class InvalidAvatarException(val tooLarge: Boolean = false) : IOException()

/** Owns temporary images so drafts survive rotation without keeping library URI permissions. */
@Singleton
class AvatarImageStore @Inject constructor(@ApplicationContext context: Context) {
    private val images = ImageFileStore(context, "avatar", 5L * 1024 * 1024)

    fun createCameraUri(): Uri = images.createCameraUri()

    suspend fun prepare(uri: Uri): PreparedAvatar = try {
        images.prepare(uri).let { PreparedAvatar(it.file, it.mimeType) }
    } catch (error: InvalidImageException) {
        throw InvalidAvatarException(tooLarge = error.tooLarge)
    }

    fun deleteDraft(path: String?) = images.deleteDraft(path)

    fun deleteCamera(uri: Uri?) = images.deleteCamera(uri)
}
