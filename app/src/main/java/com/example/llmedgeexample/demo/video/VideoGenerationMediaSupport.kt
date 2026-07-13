package com.example.llmedgeexample.demo.video

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import com.example.llmedgeexample.common.copyOpenableToCache
import io.aatricks.llmedge.vision.ImageUtils
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CachedVideoAsset(
    val file: File,
    val selectionPath: String,
)

internal object VideoGenerationMediaSupport {
    fun createImagePickerIntent(): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            },
            "Select Init Image",
        )

    fun createSafetensorPickerIntent(title: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
            },
            title,
        )

    fun loadInitImage(
        context: Context,
        uri: Uri,
    ): Bitmap? =
        context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)

    fun loadLoraFile(
        context: Context,
        uri: Uri,
    ): CachedVideoAsset {
        val file =
            context.copyOpenableToCache(
                uri = uri,
                subdirectory = "loras",
                fallbackFileName = "lora_${System.currentTimeMillis()}.safetensors",
                requiredSuffix = ".safetensors",
            )
        return CachedVideoAsset(
            file = file,
            selectionPath = file.parentFile?.absolutePath ?: file.absolutePath,
        )
    }

    fun loadTaehvFile(
        context: Context,
        uri: Uri,
    ): CachedVideoAsset {
        val file =
            context.copyOpenableToCache(
                uri = uri,
                subdirectory = "taehv",
                fallbackFileName = "taehv_${System.currentTimeMillis()}.safetensors",
                requiredSuffix = ".safetensors",
            )
        return CachedVideoAsset(
            file = file,
            selectionPath = file.absolutePath,
        )
    }

    suspend fun saveFramesAsGif(
        frames: List<Bitmap>,
        fps: Int,
    ): File =
        withContext(Dispatchers.IO) {
            val outputDir =
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "LLMEdge",
                ).apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val gifFile = File(outputDir, "video_$timestamp.gif")
            FileOutputStream(gifFile).use { output ->
                ImageUtils.createAnimatedGif(
                    frames = frames,
                    delayMs = 1000 / fps,
                    output = output,
                    loop = 0,
                )
            }
            gifFile
        }
}
