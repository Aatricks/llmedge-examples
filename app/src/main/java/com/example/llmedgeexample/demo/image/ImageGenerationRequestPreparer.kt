package com.example.llmedgeexample.demo.image

import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import io.aatricks.llmedge.model.ModelArtifactKind
import io.aatricks.llmedge.model.ModelCapability
import io.aatricks.llmedge.model.ModelHints
import io.aatricks.llmedge.model.ModelSpec
import java.io.File
import kotlinx.coroutines.CancellationException

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
    private val hyperSd3Downloader: ImageLoraAssetDownloader = HyperSd3ImageLoraDownloader,
) {
    suspend fun prepare(
        edge: LLMEdge,
        baseRequest: ImageGenerationRequest,
        loraRequested: Boolean,
        hyperSd3Requested: Boolean = false,
        onStatus: (String) -> Unit = {},
    ): PreparedImageGenerationRequest {
        require(!loraRequested || !hyperSd3Requested) {
            "Only one image LoRA can be selected at a time."
        }
        if (!loraRequested && !hyperSd3Requested) {
            return PreparedImageGenerationRequest(request = baseRequest, loraApplied = false)
        }

        return try {
            val isHyperSd3 = hyperSd3Requested
            val label = if (isHyperSd3) "Hyper-SD3 4-step" else "Detail Tweaker"
            onStatus("Downloading $label LoRA...")
            val loraFile =
                (if (isHyperSd3) hyperSd3Downloader else loraDownloader).download(
                    edge,
                ) { downloaded, total ->
                    onStatus(formatDownloadStatus(label, downloaded, total))
                }
            val loraDirectory = loraFile.parentFile?.absolutePath
            if (loraDirectory.isNullOrBlank()) {
                PreparedImageGenerationRequest(
                    request = baseRequest,
                    loraApplied = false,
                    warningMessage = "Downloaded $label LoRA has no parent directory. Continuing without LoRA.",
                )
            } else {
                PreparedImageGenerationRequest(
                    request =
                        baseRequest.copy(
                            prompt =
                                appendLoraTag(
                                    baseRequest.prompt,
                                    loraFile.nameWithoutExtension,
                                    if (isHyperSd3) 0.125f else 1.0f,
                                ),
                            steps = if (isHyperSd3) 4 else baseRequest.steps,
                            cfgScale = if (isHyperSd3) 3.0f else baseRequest.cfgScale,
                            sequential = if (isHyperSd3) true else baseRequest.sequential,
                            loraModelDir = loraDirectory,
                            loraApplyMode = LoraApplyMode.AUTO,
                        ),
                    loraApplied = true,
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            val label = if (hyperSd3Requested) "Hyper-SD3 4-step" else "Detail Tweaker"
            PreparedImageGenerationRequest(
                request = baseRequest,
                loraApplied = false,
                warningMessage = "Failed to download $label LoRA: ${t.localizedMessage ?: "unknown error"}. Continuing without LoRA.",
            )
        }
    }

    private fun formatDownloadStatus(
        label: String,
        downloaded: Long,
        total: Long?,
    ): String {
        if (total != null && total > 0L) {
            val percent = ((downloaded * 100L) / total).coerceIn(0L, 100L)
            return "Downloading $label LoRA... $percent%"
        }
        return "Downloading $label LoRA..."
    }

    private fun appendLoraTag(
        prompt: String,
        loraName: String,
        scale: Float,
    ): String =
        buildString {
            append(prompt.trim())
            if (isNotEmpty()) {
                append(' ')
            }
            append("<lora:")
            append(loraName)
            append(':')
            append(scale)
            append('>')
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

private object HyperSd3ImageLoraDownloader : ImageLoraAssetDownloader {
    override suspend fun download(
        edge: LLMEdge,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): File =
        edge.models.resolve(
            ModelSpec.huggingFace(
                repoId = "ByteDance/Hyper-SD",
                filename = "Hyper-SD3-4steps-CFG-lora.safetensors",
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
