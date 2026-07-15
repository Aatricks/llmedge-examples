package com.example.llmedgeexample.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    displayName: String
): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LLMEdge")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }

    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val uri = resolver.insert(collection, contentValues) ?: return@withContext null

    try {
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw java.io.IOException("Failed to compress bitmap")
            }
        }
        contentValues.clear()
        contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}

suspend fun saveFileToGallery(
    context: Context,
    file: java.io.File,
    mimeType: String,
    relativePath: String,
    displayName: String
): Uri? = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val collection = if (mimeType.startsWith("image/")) {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    } else if (mimeType.startsWith("video/")) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Downloads.EXTERNAL_CONTENT_URI
    }

    val uri = resolver.insert(collection, contentValues) ?: return@withContext null

    try {
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        contentValues.clear()
        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, contentValues, null, null)
        uri
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        null
    }
}
