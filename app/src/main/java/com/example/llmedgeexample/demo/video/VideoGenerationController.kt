package com.example.llmedgeexample.demo.video

import android.content.Context
import android.graphics.Bitmap
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.GenerationStreamEvent
import io.aatricks.llmedge.image.VideoGenerationRequest
import io.aatricks.llmedge.image.diffusion.EasyCacheParams
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import io.aatricks.llmedge.image.diffusion.SampleMethod
import io.aatricks.llmedge.image.diffusion.Scheduler
import io.aatricks.llmedge.model.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoGenerationConfig(
    val prompt: String,
    val width: Int,
    val height: Int,
    val frames: Int,
    val fps: Int,
    val steps: Int,
    val cfgScale: Float,
    val seed: Long,
    val flowShift: Float,
    val model: ModelSpec?,
    val vae: ModelSpec?,
    val textEncoder: ModelSpec?,
    val sampleMethod: SampleMethod,
    val scheduler: Scheduler,
    val loraDirectory: String?,
    val taehvPath: String?,
    val initImage: Bitmap?,
    val initImageStrength: Float,
    val defaultLoraDirectory: String?,
)

data class VideoGenerationResult(
    val frames: List<Bitmap>,
    val fps: Int,
    val metricsSummary: String?,
)

data class VideoGenerationCallbacks(
    val onProgress: (percent: Int, status: String) -> Unit,
    val onCompleted: (VideoGenerationResult) -> Unit,
    val onFinished: () -> Unit,
)

class VideoGenerationController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val edge: LLMEdge,
    private val tag: String = "VideoGenerationActivity",
) {
    private var generationJob: Job? = null

    fun isGenerating(): Boolean = generationJob?.isActive == true

    fun start(config: VideoGenerationConfig, callbacks: VideoGenerationCallbacks) {
        check(!isGenerating()) { "Video generation already running" }

        val isLowMemoryDevice = context.isLowRamDevice()
        val availableMemoryMb = context.availableMemoryMb()
        val actualFrames = (config.frames - 1) / 4 * 4 + 1
        val useSequentialLoad = isLowMemoryDevice || availableMemoryMb < 2500L

        FileLogger.separator("Video Generation Starting")
        FileLogger.i(tag, "Parameters:")
        FileLogger.i(
            tag,
            "  Size: ${config.width}x${config.height}, Frames: ${config.frames} (actual: $actualFrames), Steps: ${config.steps}",
        )
        FileLogger.i(tag, "  CFG: ${config.cfgScale}, Seed: ${config.seed}, FlowShift: ${config.flowShift}")
        FileLogger.i(tag, "  Sampler: ${config.sampleMethod}, Scheduler: ${config.scheduler}")
        FileLogger.i(tag, "  LoRA: ${config.loraDirectory ?: "none"}")
        FileLogger.i(tag, "  TAEHV: ${config.taehvPath ?: "none"}")
        FileLogger.i(tag, "  I2V: ${config.initImage != null}, Strength: ${config.initImageStrength}")
        FileLogger.i(
            tag,
            "  Memory: isLowMem=$isLowMemoryDevice, available=${availableMemoryMb}MB",
        )

        generationJob =
            scope.launch(Dispatchers.IO) {
                try {
                    context.logDemoMemoryState(tag, "Before video generation", includeGpu = true) {
                        FileLogger.i(tag, it)
                    }

                    val resizedInitImage =
                        config.initImage?.let { bitmap ->
                            if (bitmap.width != config.width || bitmap.height != config.height) {
                                Bitmap.createScaledBitmap(bitmap, config.width, config.height, true)
                            } else {
                                bitmap
                            }
                        }

                    FileLogger.i(tag, "Init image ready: ${resizedInitImage != null}")
                    FileLogger.i(
                        tag,
                        "Generation mode: ${if (useSequentialLoad) "sequential" else "direct"} (isLowMem=$isLowMemoryDevice, available=${availableMemoryMb}MB)",
                    )

                    withContext(Dispatchers.Main) {
                        callbacks.onProgress(0, "Preparing model...")
                    }

                    val request =
                        VideoGenerationRequest(
                            prompt = config.prompt,
                            width = config.width,
                            height = config.height,
                            videoFrames = config.frames,
                            steps = config.steps,
                            cfgScale = config.cfgScale,
                            seed = config.seed,
                            flowShift = config.flowShift,
                            model = config.model,
                            vae = config.vae,
                            textEncoder = config.textEncoder,
                            flashAttention = true,
                            forceSequentialLoad = useSequentialLoad,
                            sampleMethod = config.sampleMethod,
                            scheduler = config.scheduler,
                            loraModelDir = config.loraDirectory ?: config.defaultLoraDirectory,
                            loraApplyMode = LoraApplyMode.AUTO,
                            taehv = config.taehvPath?.let(ModelSpec::localFile),
                            initImage = resizedInitImage,
                            strength = config.initImageStrength,
                            easyCache =
                                EasyCacheParams(
                                    enabled = true,
                                    reuseThreshold = 0.2f,
                                    startPercent = 0.15f,
                                    endPercent = 0.95f,
                                ),
                        )

                    val estimator = StepEtaEstimator()
                    var frames: List<Bitmap> = emptyList()
                    edge.image.generateVideo(request).collect { event ->
                        when (event) {
                            is GenerationStreamEvent.Progress -> {
                                if (event.update.total > 0) {
                                    val snap = estimator.onStep(event.update.current, event.update.total)
                                    withContext(Dispatchers.Main) {
                                        callbacks.onProgress(snap.percent, "${event.update.message} · ${snap.label}")
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        callbacks.onProgress(0, event.update.message)
                                    }
                                }
                            }
                            is GenerationStreamEvent.Completed -> {
                                frames = event.frames
                            }
                        }
                    }

                    context.logDemoMemoryState(tag, "After video generation", includeGpu = true) {
                        FileLogger.i(tag, it)
                    }

                    val metricsSummary =
                        edge.image.getLastGenerationMetrics()?.let { metrics ->
                            "Generated ${frames.size} frames in ${String.format("%.1f", metrics.totalTimeSeconds)}s"
                        }

                    withContext(Dispatchers.Main) {
                        callbacks.onCompleted(
                            VideoGenerationResult(
                                frames = frames,
                                fps = config.fps,
                                metricsSummary = metricsSummary,
                            )
                        )
                        callbacks.onProgress(
                            100,
                            context.getString(R.string.video_status_complete, frames.size),
                        )
                    }
                } catch (_: CancellationException) {
                    withContext(Dispatchers.Main) {
                        callbacks.onProgress(0, context.getString(R.string.video_status_cancelled))
                    }
                } catch (oom: OutOfMemoryError) {
                    FileLogger.e(tag, "Out of memory during generation", oom)
                    context.logDemoMemoryState(tag, "OOM error", includeGpu = true) {
                        FileLogger.i(tag, it)
                    }
                    withContext(Dispatchers.Main) {
                        callbacks.onProgress(
                            0,
                            context.getString(
                                R.string.video_status_failed,
                                "Out of memory. Close other apps and try again.",
                            ),
                        )
                    }
                } catch (t: Throwable) {
                    FileLogger.e(tag, "Failed during generation", t)
                    val message = t.localizedMessage ?: "error"
                    val finalMessage =
                        if (
                            message.contains("Failed to initialize Stable Diffusion context") &&
                                config.taehvPath != null
                        ) {
                            "$message\n\nTip: You selected a custom TAEHV. Ensure it is compatible with Wan2.1 (e.g. taew2_1.safetensors). Incompatible TAEHVs (like SDXL's) will cause this error."
                        } else {
                            message
                        }
                    withContext(Dispatchers.Main) {
                        callbacks.onProgress(
                            0,
                            context.getString(R.string.video_status_failed, finalMessage),
                        )
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        generationJob = null
                        callbacks.onFinished()
                    }
                }
            }
    }

    fun cancel() {
        generationJob?.cancel()
        edge.image.cancelGeneration()
    }
}
