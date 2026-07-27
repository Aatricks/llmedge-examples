package com.example.llmedgeexample.common

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
    val fileName =
        getOpenableDisplayName(uri, fallbackFileName)
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .ifBlank { fallbackFileName }
    if (requiredSuffix != null && !fileName.endsWith(requiredSuffix, ignoreCase = true)) {
        throw IllegalArgumentException("Please select a $requiredSuffix file")
    }

    val targetDir = File(cacheDir, subdirectory).apply { mkdirs() }
    val targetFile = File(targetDir, fileName)
    val partialFile = File(targetDir, "${targetFile.name}.partial")
    try {
        partialFile.delete()
        contentResolver.openInputStream(uri)?.use { inputStream ->
            partialFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        } ?: error("Unable to open $fileName")
        try {
            Files.move(
                partialFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                partialFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    } catch (t: Throwable) {
        partialFile.delete()
        throw t
    }
    return targetFile
}
