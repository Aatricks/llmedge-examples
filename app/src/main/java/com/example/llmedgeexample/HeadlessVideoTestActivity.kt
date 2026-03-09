package com.example.llmedgeexample

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.LLMEdgeConfig
import io.aatricks.llmedge.image.diffusion.EasyCacheParams
import io.aatricks.llmedge.image.diffusion.GenerationMetrics
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import io.aatricks.llmedge.image.GenerationStreamEvent
import io.aatricks.llmedge.image.VideoGenerationRequest
import io.aatricks.llmedge.model.ModelSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Headless activity for E2E video generation testing. Launches programmatically, generates video,
 * logs results, and exits.
 *
 * Usage: adb shell am start -n com.example.llmedgeexample/.HeadlessVideoTestActivity \ --es prompt
 * "a cat walking" \ --ei frames 8 \ --ei width 256 \ --ei height 256 \ --ei steps 20 \ --ef
 * cfg_scale 7.0 \ --el seed 42 \ --es taehv_path /sdcard/Download/taew2_1.safetensors \ --ez
 * force_sequential false \ --ez prefer_performance_mode true
 */
class HeadlessVideoTestActivity : Activity() {

    // Use IO dispatcher for native JNI operations for better performance
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "VideoE2E"
        private const val DEFAULT_PROMPT = "a cat walking in a garden, high quality"
        private const val DEFAULT_FRAMES = 8
        private const val DEFAULT_WIDTH = 256
        private const val DEFAULT_HEIGHT = 256
        private const val DEFAULT_STEPS = 20
        private const val DEFAULT_CFG_SCALE = 7.0f
        private const val DEFAULT_SEED = 42L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set a minimal view to prevent immediate finish
        setContentView(android.R.layout.simple_list_item_1)

        android.util.Log.e(TAG, "========================================")
        android.util.Log.e(TAG, "Headless Video Generation E2E Test")
        android.util.Log.e(TAG, "========================================")

        // Extract parameters from intent
        val prompt = intent.getStringExtra("prompt") ?: DEFAULT_PROMPT
        val frames = intent.getIntExtra("frames", DEFAULT_FRAMES)
        val width = intent.getIntExtra("width", DEFAULT_WIDTH)
        val height = intent.getIntExtra("height", DEFAULT_HEIGHT)
        val steps = intent.getIntExtra("steps", DEFAULT_STEPS)
        val cfgScale = intent.getFloatExtra("cfg_scale", DEFAULT_CFG_SCALE)
        val seed = intent.getLongExtra("seed", DEFAULT_SEED)
        val taehvPath = intent.getStringExtra("taehv_path")
        val forceSequential = intent.getBooleanExtra("force_sequential", false)
        val preferPerformanceMode = intent.getBooleanExtra("prefer_performance_mode", false)

        android.util.Log.e(TAG, "Parameters:")
        android.util.Log.e(TAG, "  Prompt: $prompt")
        android.util.Log.e(TAG, "  Frames: $frames")
        android.util.Log.e(TAG, "  Resolution: ${width}x${height}")
        android.util.Log.e(TAG, "  Steps: $steps")
        android.util.Log.e(TAG, "  CFG Scale: $cfgScale")
        android.util.Log.e(TAG, "  Seed: $seed")
        android.util.Log.e(TAG, "  TAEHV path: ${taehvPath ?: "(none)"}")
        android.util.Log.e(TAG, "  force_sequential: $forceSequential")
        android.util.Log.e(TAG, "  prefer_performance_mode: $preferPerformanceMode")
        android.util.Log.e(TAG, "")

        // Start generation (don't finish until complete)
        scope.launch {
            runTest(
                    prompt,
                    frames,
                    width,
                    height,
                    steps,
                    cfgScale,
                    seed,
                    taehvPath,
                    forceSequential,
                    preferPerformanceMode,
            )
            // Finish activity after test completes
            withContext(Dispatchers.Main) { finish() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.e(TAG, "Activity destroying, cancelling scope")
        scope.cancel()
    }

    private suspend fun runTest(
            prompt: String,
            frames: Int,
            width: Int,
            height: Int,
            steps: Int,
            cfgScale: Float,
            seed: Long,
            taehvPath: String?,
            forceSequential: Boolean,
            preferPerformanceMode: Boolean,
    ) {
        val startTime = System.currentTimeMillis()

        try {
            android.util.Log.e(TAG, "Starting video generation using LLMEdge...")
            val genStartTime = System.currentTimeMillis()
            val edge =
                    LLMEdge.create(
                            applicationContext,
                            scope,
                            LLMEdgeConfig(preferPerformanceMode = preferPerformanceMode),
                    )
            val params =
                    VideoGenerationRequest(
                            prompt = prompt,
                            videoFrames = frames,
                            width = width,
                            height = height,
                            steps = steps,
                            cfgScale = cfgScale,
                            seed = seed,
                            flashAttention = true,
                            forceSequentialLoad = forceSequential,
                            taehv = taehvPath?.let(ModelSpec::localFile),
                            easyCache =
                                    EasyCacheParams(
                                            enabled = true
                                    ),
                            loraModelDir = null,
                            loraApplyMode = LoraApplyMode.AUTO,
                    )

            var lastProgressTime = genStartTime
            var generatedFrames = emptyList<android.graphics.Bitmap>()
            var metrics: GenerationMetrics? = null
            try {
                edge.image.generateVideo(params).collect { event ->
                    when (event) {
                        is GenerationStreamEvent.Progress -> {
                            val now = System.currentTimeMillis()
                            val elapsed = (now - lastProgressTime) / 1000.0
                            if (elapsed > 1.0 || event.update.current == event.update.total) {
                                lastProgressTime = now
                                val percent =
                                        if (event.update.total > 0) {
                                            (event.update.current.toFloat() / event.update.total * 100).toInt()
                                        } else {
                                            0
                                        }
                                android.util.Log.e(
                                        TAG,
                                        "Progress: ${event.update.message} (${event.update.current}/${event.update.total}) - $percent%"
                                )
                            }
                        }
                        is GenerationStreamEvent.Completed -> {
                            generatedFrames = event.frames
                        }
                    }
                }
                metrics = edge.image.getLastGenerationMetrics()
            } finally {
                edge.close()
            }

            val genTime = System.currentTimeMillis() - genStartTime
            android.util.Log.e(TAG, "")
            android.util.Log.e(TAG, "✓ Video generation completed in ${genTime}ms")
            android.util.Log.e(TAG, "")

            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "Results Summary")
            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "Status: SUCCESS")
            android.util.Log.e(TAG, "Generated frames: ${generatedFrames.size}")
            android.util.Log.e(TAG, "Resolution: ${width}x${height}")
            android.util.Log.e(
                    TAG,
                    "Total time: ${(System.currentTimeMillis() - startTime) / 1000.0}s"
            )

            if (metrics != null) {
                android.util.Log.e(
                        TAG,
                        "Generation time: ${String.format("%.2f", metrics.totalTimeSeconds)}s"
                )
                android.util.Log.e(
                        TAG,
                        "Frames/sec: ${String.format("%.2f", metrics.framesPerSecond)}"
                )
                android.util.Log.e(TAG, "Memory used: ${metrics.peakMemoryUsageMb}MB")
            }

            android.util.Log.e(TAG, "========================================")
            android.util.Log.e(TAG, "")

            // Validate frames
            if (generatedFrames.size != frames) {
                Log.e(TAG, "❌ Frame count mismatch: expected $frames, got ${generatedFrames.size}")
            } else {
                android.util.Log.e(TAG, "✓ Frame count validated")
            }

            generatedFrames.forEachIndexed { index, bitmap ->
                if (bitmap.width != width || bitmap.height != height) {
                    Log.e(
                            TAG,
                            "❌ Frame $index resolution mismatch: ${bitmap.width}x${bitmap.height}"
                    )
                } else if (index == 0) {
                    android.util.Log.e(TAG, "✓ Frame resolution validated")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "========================================")
            Log.e(TAG, "Test FAILED")
            Log.e(TAG, "========================================")
            Log.e(TAG, "Error: ${e.message}", e)
            Log.e(TAG, "========================================")
        }
    }
}
