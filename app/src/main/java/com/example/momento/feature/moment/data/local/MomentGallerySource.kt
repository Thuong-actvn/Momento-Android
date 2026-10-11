package com.example.momento.feature.moment.data.local

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.momento.feature.moment.domain.model.GalleryImage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Reads only the images Android currently allows this app to access. */
class MomentGallerySource @Inject constructor(@param:ApplicationContext private val context: Context) {
    suspend fun recentImages(): List<GalleryImage> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val images = mutableListOf<GalleryImage>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.MIME_TYPE} IN (?, ?, ?)",
            arrayOf("image/jpeg", "image/png", "image/webp"),
            "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (images.size < 200 && cursor.moveToNext()) {
                ensureActive()
                val id = cursor.getLong(idColumn)
                images += GalleryImage(id.toString(), ContentUris.withAppendedId(collection, id).toString())
            }
        } ?: error("MediaStore query returned no cursor")
        images
    }
}
