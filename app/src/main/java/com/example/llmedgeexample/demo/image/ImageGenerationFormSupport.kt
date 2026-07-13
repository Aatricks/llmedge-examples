package com.example.llmedgeexample.demo.image

import com.example.llmedgeexample.common.GenerationDemoSupport
import io.aatricks.llmedge.image.Flux2Klein
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.MiniT2I
import io.aatricks.llmedge.image.diffusion.LoraApplyMode

internal enum class ImageGenerationModel {
    DEFAULT,
    FLUX2_KLEIN,
    MINI_T2I,
}

internal object ImageGenerationFormSupport {
    fun createRequest(
        model: ImageGenerationModel,
        prompt: String,
        width: Int,
        height: Int,
        steps: Int,
        cfgScale: Float,
        seed: Long,
        flashAttention: Boolean,
    ): ImageGenerationRequest =
        when (model) {
            ImageGenerationModel.FLUX2_KLEIN ->
                Flux2Klein.bonsaiImageRequest(
                    prompt = prompt,
                    width = width,
                    height = height,
                    seed = seed,
                    flashAttention = flashAttention,
                )
            ImageGenerationModel.MINI_T2I ->
                MiniT2I.imageRequest(
                    prompt = prompt,
                    width = width,
                    height = height,
                    steps = steps,
                    cfgScale = cfgScale,
                    seed = seed,
                    flashAttention = flashAttention,
                )
            ImageGenerationModel.DEFAULT ->
                ImageGenerationRequest(
                    prompt = prompt,
                    width = width,
                    height = height,
                    steps = steps,
                    cfgScale = cfgScale,
                    seed = seed,
                    flashAttention = flashAttention,
                    forceSequentialLoad = false,
                    loraModelDir = null,
                    loraApplyMode = LoraApplyMode.AUTO,
                )
        }

    fun parseDimensionField(
        field: android.widget.EditText,
        defaultValue: Int,
        label: String,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "$label must be a multiple of 8 between 128 and 1024",
        ) { value ->
            value in 128..1024 && value % 8 == 0
        }

    fun parseStepsField(
        field: android.widget.EditText,
        defaultValue: Int,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Steps must be between 1 and 100",
        ) { value ->
            value in 1..100
        }

    fun parseCfgField(
        field: android.widget.EditText,
        defaultValue: Float,
    ): Float? =
        GenerationDemoSupport.parseFloatField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "CFG must be between 1.0 and 15.0",
        ) { value ->
            value in 1.0f..15.0f
        }

    fun parseSeedField(
        field: android.widget.EditText,
        defaultValue: Long,
    ): Long? =
        GenerationDemoSupport.parseLongField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Seed must be -1 or non-negative",
        ) { value ->
            value >= -1L
        }
}
