package com.example.llmedgeexample.demo.image

import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import io.aatricks.llmedge.model.ModelArtifactKind
import io.aatricks.llmedge.model.ModelCapability
import io.aatricks.llmedge.model.ModelHints
import io.aatricks.llmedge.model.ModelSpec
import java.io.File

internal data class PreparedImageGenerationRequest(
    val request: ImageGenerationRequest,
    val loraApplied: Boolean,
    val warningMessage: String? = null,
)

internal fun interface ImageLoraAssetDownloader {
    suspend fun download(
        edge: LLMEdge,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File
}

internal class ImageGenerationRequestPreparer(
    private val loraDownloader: ImageLoraAssetDownloader = DetailTweakerImageLoraDownloader,
) {
    suspend fun prepare(
        edge: LLMEdge,
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
                loraDownloader.download(edge) { downloaded, total ->
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
        edge: LLMEdge,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File =
        edge.models.resolve(
            ModelSpec.huggingFace(
                repoId = "imagepipeline/Detail-Tweaker-LoRA-SD1.5",
                filename = "add_detail.safetensors",
                preferredQuantizations = emptyList(),
                hints =
                    ModelHints(
                        artifactKind = ModelArtifactKind.REPO_FILE,
                        capabilities = setOf(ModelCapability.IMAGE),
                    ),
            ),
            onProgress = { progress ->
                onProgress(progress.downloadedBytes, progress.totalBytes)
            },
        )
}

internal fun interface UpscalerAssetDownloader {
    suspend fun download(
        edge: LLMEdge,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File
}

internal object RemacriUpscalerDownloader : UpscalerAssetDownloader {
    override suspend fun download(
        edge: LLMEdge,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File =
        edge.models.resolve(
            ModelSpec.huggingFace(
                repoId = "LyliaEngine/4x_foolhardy_Remacri",
                filename = "4x_foolhardy_Remacri.safetensors",
                preferredQuantizations = emptyList(),
                hints =
                    ModelHints(
                        artifactKind = ModelArtifactKind.REPO_FILE,
                        capabilities = setOf(ModelCapability.IMAGE),
                    ),
            ),
            onProgress = { progress ->
                onProgress(progress.downloadedBytes, progress.totalBytes)
            },
        )
}
