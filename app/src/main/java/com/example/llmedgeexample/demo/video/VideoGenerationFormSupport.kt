package com.example.llmedgeexample.demo.video

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import com.example.llmedgeexample.common.GenerationDemoSupport
import io.aatricks.llmedge.image.diffusion.SampleMethod
import io.aatricks.llmedge.image.diffusion.Scheduler
import io.aatricks.llmedge.model.ModelArtifactKind
import io.aatricks.llmedge.model.ModelCapability
import io.aatricks.llmedge.model.ModelHints
import io.aatricks.llmedge.model.ModelSpec

internal data class VideoModelPreset(
    val displayName: String,
    val model: ModelSpec? = null,
    val vae: ModelSpec? = null,
    val textEncoder: ModelSpec? = null,
)

internal object VideoGenerationFormSupport {
    private const val WAN_21_GGUF_REPO = "samuelchristlie/Wan2.1-T2V-1.3B-GGUF"
    private const val WAN_21_REPO = "Comfy-Org/Wan_2.1_ComfyUI_repackaged"
    private const val WAN_T5_REPO = "city96/umt5-xxl-encoder-gguf"
    private const val WAN_22_REPO = "QuantStack/Wan2.2-TI2V-5B-GGUF"
    private val recommendedSamplers = SampleMethod.WAN_RECOMMENDED
    private val orderedSamplers =
        recommendedSamplers + SampleMethod.values().filter { it !in recommendedSamplers }
    val modelPresets =
        listOf(
            VideoModelPreset(
                displayName = "Wan 2.1 T2V 1.3B Q3_K_S (mobile default)",
                model =
                    ModelSpec.huggingFace(
                        repoId = WAN_21_GGUF_REPO,
                        filename = "Wan2.1-T2V-1.3B-Q3_K_S.gguf",
                        hints =
                            ModelHints(
                                artifactKind = ModelArtifactKind.DIFFUSION_MODEL,
                                capabilities = setOf(ModelCapability.IMAGE, ModelCapability.VIDEO),
                            ),
                    ),
                vae =
                    ModelSpec.huggingFace(
                        repoId = WAN_21_REPO,
                        filename = "wan_2.1_vae.safetensors",
                        hints =
                            ModelHints(
                                artifactKind = ModelArtifactKind.VAE,
                                capabilities = setOf(ModelCapability.VIDEO),
                            ),
                    ),
                textEncoder = wanTextEncoder(),
            ),
            VideoModelPreset(
                displayName = "Wan 2.1 T2V 1.3B fp16 (high memory)",
                model =
                    ModelSpec.huggingFace(
                        repoId = WAN_21_REPO,
                        filename = "wan2.1_t2v_1.3B_fp16.safetensors",
                        hints =
                            ModelHints(
                                artifactKind = ModelArtifactKind.DIFFUSION_MODEL,
                                capabilities = setOf(ModelCapability.IMAGE, ModelCapability.VIDEO),
                            ),
                    ),
                vae =
                    ModelSpec.huggingFace(
                        repoId = WAN_21_REPO,
                        filename = "wan_2.1_vae.safetensors",
                        hints =
                            ModelHints(
                                artifactKind = ModelArtifactKind.VAE,
                                capabilities = setOf(ModelCapability.VIDEO),
                            ),
                    ),
                textEncoder = wanTextEncoder(),
            ),
            VideoModelPreset(
                displayName = "Wan 2.2 TI2V 5B Q6_K",
                model =
                    ModelSpec.huggingFace(
                        repoId = WAN_22_REPO,
                        filename = "Wan2.2-TI2V-5B-Q6_K.gguf",
                        hints =
                            ModelHints(
                                artifactKind = ModelArtifactKind.DIFFUSION_MODEL,
                                capabilities = setOf(ModelCapability.IMAGE, ModelCapability.VIDEO),
                            ),
                    ),
                vae =
                    ModelSpec.huggingFace(
                        repoId = WAN_22_REPO,
                        filename = "VAE/Wan2.2_VAE.safetensors",
                        hints =
                            ModelHints(
                                artifactKind = ModelArtifactKind.VAE,
                                capabilities = setOf(ModelCapability.VIDEO),
                            ),
                    ),
            ),
        )

    private fun wanTextEncoder(): ModelSpec =
        ModelSpec.huggingFace(
            repoId = WAN_T5_REPO,
            filename = "umt5-xxl-encoder-Q3_K_S.gguf",
            hints =
                ModelHints(
                    artifactKind = ModelArtifactKind.TEXT_ENCODER,
                    capabilities = setOf(ModelCapability.TEXT, ModelCapability.VIDEO),
                ),
        )

    fun bindAdapters(
        context: Context,
        modelSpinner: Spinner,
        samplerSpinner: Spinner,
        schedulerSpinner: Spinner,
    ) {
        modelSpinner.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                modelPresets.map(VideoModelPreset::displayName),
            )
        val samplerNames =
            orderedSamplers.map { sampleMethod ->
                val displayName = sampleMethod.name.replace("_", " ")
                if (sampleMethod in recommendedSamplers) "$displayName ★" else displayName
            }
        samplerSpinner.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                samplerNames,
            )

        val schedulerNames = Scheduler.values().map { it.name.replace("_", " ") }
        schedulerSpinner.adapter =
            ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                schedulerNames,
            )
    }

    fun selectedSampleMethod(position: Int): SampleMethod =
        orderedSamplers.getOrElse(position) { SampleMethod.DEFAULT }

    fun selectedScheduler(position: Int): Scheduler =
        Scheduler.values().getOrElse(position) { Scheduler.DEFAULT }

    fun selectedModelPreset(position: Int): VideoModelPreset =
        modelPresets.getOrElse(position) { modelPresets.first() }

    fun withModelOverride(
        preset: VideoModelPreset,
        modelOverride: ModelSpec?,
    ): VideoModelPreset =
        if (modelOverride == null) {
            preset
        } else {
            preset.copy(model = modelOverride)
        }

    fun parseDimensionField(
        field: EditText,
        defaultValue: Int,
        label: String,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "$label must be a multiple of 64 between 256 and 960",
        ) { value ->
            value in 256..960 && value % 64 == 0
        }

    fun parseFramesField(
        field: EditText,
        defaultValue: Int,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Frames must be between 1 and 64",
        ) { value ->
            value in 1..64
        }

    fun parseFpsField(
        field: EditText,
        defaultValue: Int,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "FPS must be between 1 and 30",
        ) { value ->
            value in 1..30
        }

    fun parseStepsField(
        field: EditText,
        defaultValue: Int,
    ): Int? =
        GenerationDemoSupport.parseIntField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Steps must be between 1 and 50",
        ) { value ->
            value in 1..50
        }

    fun parseCfgField(
        field: EditText,
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
        field: EditText,
        defaultValue: Long,
    ): Long? =
        GenerationDemoSupport.parseLongField(
            field = field,
            defaultValue = defaultValue,
            errorMessage = "Seed must be -1 or non-negative",
        ) { value ->
            value >= -1L
        }

    fun parseFlowShiftField(field: EditText): Float? {
        val raw = field.text.toString().trim()
        if (raw.isBlank()) {
            field.error = null
            return Float.POSITIVE_INFINITY
        }
        return GenerationDemoSupport.parseFloatField(
            field = field,
            defaultValue = raw.toFloatOrNull() ?: Float.NaN,
            errorMessage = "Flow shift must be greater than 0",
        ) { value ->
            value > 0f
        }
    }
}
