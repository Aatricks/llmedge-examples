package com.example.llmedgeexample.demo.image

import io.aatricks.llmedge.image.Flux2Klein
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.MiniT2I
import io.aatricks.llmedge.image.Sd3Medium
import io.aatricks.llmedge.image.ChromaRadiance
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import io.aatricks.llmedge.model.ModelSpec

internal enum class ImageModelPreset(
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultSteps: Int,
    val defaultCfg: Float,
    val supportsLora: Boolean,
) {
    SD15(512, 512, 20, 7.0f, true),
    FLUX2_KLEIN_BONSAI(512, 512, 4, 1.0f, false),
    SD3_MEDIUM(512, 512, 28, 4.5f, false),
    MINI_T2I(512, 512, 100, 6.0f, false),
    MINI_T2I_LARGE(512, 512, 100, 6.0f, false),
    CHROMA_MOBILE(512, 512, 20, 4.0f, false),
    CHROMA_RADIANCE(512, 512, 20, 4.0f, false);

    fun buildRequest(
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfg: Float,
        seed: Long,
        flashAttention: Boolean,
        negative: String = "",
        modelOverride: ModelSpec? = null,
        easyCacheEnabled: Boolean? = null,
    ): ImageGenerationRequest {
        val request = when (this) {
            SD15 -> ImageGenerationRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
                forceSequentialLoad = false,
                loraModelDir = null,
                loraApplyMode = LoraApplyMode.AUTO,
            )
            FLUX2_KLEIN_BONSAI -> Flux2Klein.bonsaiImageRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
            )
            SD3_MEDIUM -> Sd3Medium.imageRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
            )
            MINI_T2I -> MiniT2I.imageRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
            )
            MINI_T2I_LARGE -> MiniT2I.largeImageRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
            )
            CHROMA_MOBILE -> ChromaRadiance.mobileImageRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
            )
            CHROMA_RADIANCE -> ChromaRadiance.imageRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = flashAttention,
            )
        }
        return request.copy(
            negative = negative,
            model = modelOverride ?: request.model,
            easyCache =
                request.easyCache.copy(
                    enabled = easyCacheEnabled ?: request.easyCache.enabled,
                ),
        )
    }
}
