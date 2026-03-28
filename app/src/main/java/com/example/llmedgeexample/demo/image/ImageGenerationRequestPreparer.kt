package com.example.llmedgeexample.demo.image

import android.content.Context
import io.aatricks.llmedge.huggingface.HuggingFaceHub
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import java.io.File

internal data class PreparedImageGenerationRequest(
    val request: ImageGenerationRequest,
    val loraApplied: Boolean,
    val warningMessage: String? = null,
)

internal fun interface ImageLoraAssetDownloader {
    suspend fun download(
        context: Context,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File
}

internal class ImageGenerationRequestPreparer(
    private val loraDownloader: ImageLoraAssetDownloader = DetailTweakerImageLoraDownloader,
) {
    suspend fun prepare(
        context: Context,
        baseRequest: ImageGenerationRequest,
        loraRequested: Boolean,
        onStatus: (String) -> Unit = {},
    ): PreparedImageGenerationRequest {
        if (!loraRequested) {
            return PreparedImageGenerationRequest(request = baseRequest, loraApplied = false)
        }

        return try {
            onStatus("Downloading Detail Tweaker LoRA...")
            val loraFile =
                loraDownloader.download(context) { downloaded, total ->
                    onStatus(formatDownloadStatus(downloaded, total))
                }
            val loraDirectory = loraFile.parentFile?.absolutePath
            if (loraDirectory.isNullOrBlank()) {
                PreparedImageGenerationRequest(
                    request = baseRequest,
                    loraApplied = false,
                    warningMessage = "Downloaded Detail Tweaker LoRA has no parent directory. Continuing without LoRA.",
                )
            } else {
                PreparedImageGenerationRequest(
                    request =
                        baseRequest.copy(
                            prompt = appendLoraTag(baseRequest.prompt, loraFile.nameWithoutExtension),
                            loraModelDir = loraDirectory,
                            loraApplyMode = LoraApplyMode.AUTO,
                        ),
                    loraApplied = true,
                )
            }
        } catch (t: Throwable) {
            PreparedImageGenerationRequest(
                request = baseRequest,
                loraApplied = false,
                warningMessage = "Failed to download Detail Tweaker LoRA: ${t.localizedMessage ?: "unknown error"}. Continuing without LoRA.",
            )
        }
    }

    private fun formatDownloadStatus(downloaded: Long, total: Long?): String {
        if (total != null && total > 0L) {
            val percent = ((downloaded * 100L) / total).coerceIn(0L, 100L)
            return "Downloading Detail Tweaker LoRA... $percent%"
        }
        return "Downloading Detail Tweaker LoRA..."
    }

    private fun appendLoraTag(
        prompt: String,
        loraName: String,
    ): String =
        buildString {
            append(prompt.trim())
            if (isNotEmpty()) {
                append(' ')
            }
            append("<lora:")
            append(loraName)
            append(":1.0>")
        }
}

private object DetailTweakerImageLoraDownloader : ImageLoraAssetDownloader {
    override suspend fun download(
        context: Context,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File =
        HuggingFaceHub.ensureRepoFileOnDisk(
            context = context,
            modelId = "imagepipeline/Detail-Tweaker-LoRA-SD1.5",
            filename = "add_detail.safetensors",
            allowedExtensions = listOf(".safetensors"),
            onProgress = onProgress,
        ).file
}
