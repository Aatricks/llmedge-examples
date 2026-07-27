package com.example.llmedgeexample.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import io.aatricks.llmedge.model.ModelFileValidator
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
        val expectedSizeBytes =
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex).takeIf { it >= 0L }
                    } else {
                        null
                    }
                }
        val input = context.contentResolver.openInputStream(uri) ?: error("Unable to open $displayName")
        return input.use {
            copyToAppStorage(
                context = context,
                displayName = displayName,
                input = it,
                internalNamePrefix = internalNamePrefix,
                expectedSizeBytes = expectedSizeBytes,
            )
        }
    }

    fun copyToAppStorage(
        context: Context,
        displayName: String,
        input: InputStream,
        internalNamePrefix: String = "",
        expectedSizeBytes: Long? = null,
        availableBytesProvider: (File) -> Long = { directory ->
            android.os.StatFs(directory.absolutePath).availableBytes
        },
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
        if (expectedSizeBytes != null) {
            val availableBytes = availableBytesProvider(targetDirectory)
            check(
                expectedSizeBytes <= availableBytes &&
                    availableBytes - expectedSizeBytes >= MIN_FREE_SPACE_HEADROOM_BYTES,
            ) {
                "Not enough free storage to import $safeDisplayName"
            }
        }
        val safePrefix = internalNamePrefix.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        val slotPrefix = safePrefix.ifBlank { "model-" }
        val partialFile = File(targetDirectory, "${slotPrefix}import.partial")
        var targetFile: File? = null
        try {
            partialFile.delete()
            val digest = MessageDigest.getInstance("SHA-256")
            partialFile.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
            ModelFileValidator.requireGgufFile(partialFile.absolutePath, "Imported model")
            val contentHash = digest.digest().joinToString("") { "%02x".format(it) }
            val destination = File(targetDirectory, "$slotPrefix$contentHash.gguf")
            targetFile = destination
            try {
                Files.move(
                    partialFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    partialFile.toPath(),
                    destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            targetDirectory
                .listFiles()
                .orEmpty()
                .filter { it != destination && it.name.startsWith(slotPrefix) }
                .forEach(File::delete)
        } catch (t: Throwable) {
            partialFile.delete()
            throw t
        }
        return ImportedModel(
            file = requireNotNull(targetFile),
            displayName = safeDisplayName,
        )
    }

    fun deleteFromAppStorage(
        context: Context,
        file: File,
    ): Boolean {
        val targetDirectory = File(context.filesDir, "imported-models").canonicalFile
        val targetFile = file.canonicalFile
        if (targetFile.parentFile != targetDirectory) {
            return false
        }
        return !targetFile.exists() || targetFile.delete()
    }

    private const val MIN_FREE_SPACE_HEADROOM_BYTES = 64L * 1024L * 1024L
}
