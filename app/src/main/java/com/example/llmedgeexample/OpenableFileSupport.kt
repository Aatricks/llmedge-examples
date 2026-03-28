package com.example.llmedgeexample

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

fun Context.getOpenableDisplayName(uri: Uri, fallbackName: String): String =
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            uri.lastPathSegment ?: fallbackName
        }
    } ?: (uri.lastPathSegment ?: fallbackName)

fun Context.copyOpenableToCache(
    uri: Uri,
    subdirectory: String,
    fallbackFileName: String,
    requiredSuffix: String? = null,
): File {
    val fileName = getOpenableDisplayName(uri, fallbackFileName)
    if (requiredSuffix != null && !fileName.endsWith(requiredSuffix, ignoreCase = true)) {
        throw IllegalArgumentException("Please select a $requiredSuffix file")
    }

    val targetDir = File(cacheDir, subdirectory).apply { mkdirs() }
    val targetFile = File(targetDir, fileName)
    contentResolver.openInputStream(uri)?.use { inputStream ->
        targetFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
    } ?: error("Unable to open $fileName")
    return targetFile
}

