package com.example.llmedgeexample.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.aatricks.llmedge.model.ModelFileValidator
import java.io.File
import java.io.InputStream

internal data class ImportedModel(
    val file: File,
    val displayName: String,
)

internal object ImportedModelSupport {
    fun createPickerIntent(title: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/octet-stream"
                addCategory(Intent.CATEGORY_OPENABLE)
            },
            title,
        )

    fun copyToAppStorage(
        context: Context,
        uri: Uri,
        internalNamePrefix: String = "",
    ): ImportedModel {
        val displayName =
            context.getOpenableDisplayName(
                uri = uri,
                fallbackName = "imported_${System.currentTimeMillis()}.gguf",
            )
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open $displayName")
        return input.use {
            copyToAppStorage(
                context = context,
                displayName = displayName,
                input = it,
                internalNamePrefix = internalNamePrefix,
            )
        }
    }

    fun copyToAppStorage(
        context: Context,
        displayName: String,
        input: InputStream,
        internalNamePrefix: String = "",
    ): ImportedModel {
        val safeDisplayName =
            displayName
                .substringAfterLast('/')
                .substringAfterLast('\\')
                .ifBlank { "imported_${System.currentTimeMillis()}.gguf" }
        require(safeDisplayName.endsWith(".gguf", ignoreCase = true)) {
            "Please select a .gguf file"
        }

        val targetDirectory = File(context.filesDir, "imported-models").apply { mkdirs() }
        val safePrefix = internalNamePrefix.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val baseName = safeDisplayName.substringBeforeLast('.')
        val targetFile =
            File(
                targetDirectory,
                "$safePrefix${baseName}_${System.currentTimeMillis()}.gguf",
            )
        val partialFile = File(targetDirectory, "${targetFile.name}.partial")
        try {
            partialFile.outputStream().use(input::copyTo)
            ModelFileValidator.requireGgufFile(partialFile.absolutePath, "Imported model")
            if (!partialFile.renameTo(targetFile)) {
                partialFile.copyTo(targetFile)
                partialFile.delete()
            }
        } catch (t: Throwable) {
            partialFile.delete()
            targetFile.delete()
            throw t
        }
        return ImportedModel(
            file = targetFile,
            displayName = safeDisplayName,
        )
    }
}
