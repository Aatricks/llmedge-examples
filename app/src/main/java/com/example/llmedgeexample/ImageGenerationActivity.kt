package com.example.llmedgeexample

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.image.diffusion.LoraApplyMode
import io.aatricks.llmedge.image.ImageGenerationRequest
import io.aatricks.llmedge.image.diffusion.GenerationMetrics

/**
 * Activity for image generation using MeinaMix SD 1.5 model.
 * 
 * Optimized for memory efficiency with:
 * - Proper model loading/unloading via LLMEdge
 * - Memory state logging for debugging
 * - Graceful error handling for OOM
 */
class ImageGenerationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ImageGenerationActivity"
        private const val DEFAULT_PROMPT = "A futuristic city"
        private const val DEFAULT_WIDTH = 512
        private const val DEFAULT_HEIGHT = 512
        private const val DEFAULT_STEPS = 20
        private const val DEFAULT_CFG = 7.0f
        private const val DEFAULT_SEED = -1L
    }

    private val promptInput: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoPromptInput) }
    private val widthInput: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imageWidthInput) }
    private val heightInput: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imageHeightInput) }
    private val stepsInput: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imageStepsInput) }
    private val cfgInput: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imageCfgInput) }
    private val seedInput: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imageSeedInput) }
    private val generateButton: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnGenerateVideo) }
    private val cancelButton: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnCancelVideo) }
    private val progressBar: ProgressBar by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoProgressBar) }
    private val progressLabel: TextView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoProgressLabel) }
    private val previewImage: ImageView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoPreview) }
    private val metricsLabel: TextView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoMetricsLabel) }
    private val loraToggle: Switch by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.loraToggle) }

    private val edge by lazy(LazyThreadSafetyMode.NONE) {
        bindEdge(this, this, lifecycleScope, preferPerformanceMode = true)
    }
    private val controller by lazy(LazyThreadSafetyMode.NONE) {
        ImageGenerationController(
            scope = lifecycleScope,
            runtime = EdgeImageGenerationRuntime(edge),
            tag = TAG,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_generation) // Use image-specific layout

        generateButton.text = "Generate Image"

        progressBar.max = 100
        progressBar.progress = 0
        progressBar.visibility = View.GONE

        generateButton.setOnClickListener { startGeneration() }
        cancelButton.setOnClickListener { cancelGeneration() }

        loraToggle.setOnCheckedChangeListener { _, isChecked ->
            // Optionally, provide feedback to the user or log the state change
            if (isChecked) {
                Toast.makeText(this, "Detail Tweaker LoRA Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Detail Tweaker LoRA Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Log initial memory state
        logDemoMemoryState(TAG, "Activity created", includeGpu = true)
    }

    private fun startGeneration() {
        if (controller.isGenerating()) {
            Toast.makeText(this, "Generation already running", Toast.LENGTH_SHORT).show()
            return
        }

        val width = parseDimensionField(widthInput, DEFAULT_WIDTH, "Width") ?: return
        val height = parseDimensionField(heightInput, DEFAULT_HEIGHT, "Height") ?: return
        val steps = parseStepsField() ?: return
        val cfg = parseCfgField() ?: return
        val seed = parseSeedField() ?: return

        // Check available memory
        val availMemMB = availableMemoryMb()
        android.util.Log.i(TAG, "Starting generation with ${availMemMB}MB available")
        
        if (availMemMB < 2000) {
            Toast.makeText(
                this,
                "Low memory (${availMemMB}MB). Close other apps for better results.",
                Toast.LENGTH_LONG
            ).show()
        }

        updateProgressUI(0, "Loading model...")
        progressBar.visibility = View.VISIBLE
        progressBar.isIndeterminate = true
        generateButton.isEnabled = false
        val prompt = promptInput.text.toString().ifBlank { DEFAULT_PROMPT }
        val loraRequested = loraToggle.isChecked

        if (loraRequested) {
            android.util.Log.w(
                TAG,
                "Image LoRA toggle requested, but current JNI image generation binding does not support per-generation LoRA application. Continuing without LoRA.",
            )
            Toast.makeText(
                this,
                "Image LoRA is not supported in the current runtime. Generating without it.",
                Toast.LENGTH_LONG,
            ).show()
        }

        logDemoMemoryState(TAG, "Before image generation", includeGpu = true)

        val useFlashAttn = width >= 512 && height >= 512
        val params =
            ImageGenerationRequest(
                prompt = prompt,
                width = width,
                height = height,
                steps = steps,
                cfgScale = cfg,
                seed = seed,
                flashAttention = useFlashAttn,
                forceSequentialLoad = false,
                loraModelDir = null,
                loraApplyMode = LoraApplyMode.AUTO,
            )
        android.util.Log.i(
            TAG,
            "Submitting image request: width=${params.width}, height=${params.height}, steps=${params.steps}, " +
                "flash=${params.flashAttention}, easyCache=${params.easyCache.enabled}, sequential=${params.forceSequentialLoad}, " +
                "lora=$loraRequested",
        )

        controller.start(
            config =
                ImageGenerationConfig(
                    request = params,
                    loraRequested = loraRequested,
                ),
            callbacks =
                ImageGenerationCallbacks(
                    onProgress = ::updateProgressUI,
                    onCompleted = { result ->
                        logDemoMemoryState(TAG, "After image generation", includeGpu = true)
                        previewImage.setImageBitmap(result.bitmap)
                        result.metrics?.imageRequestMetrics?.let { imageMetrics ->
                            android.util.Log.i(
                                TAG,
                                "Image request completed: requestId=${result.requestId}, warmRuntime=${imageMetrics.cacheHit}, " +
                                    "loadMs=${imageMetrics.modelLoadMs}, generateMs=${imageMetrics.generateMs}, totalMs=${imageMetrics.totalWallTimeMs}, " +
                                    "flash=${imageMetrics.flashAttentionEnabled}, easyCache=${imageMetrics.easyCacheEnabled}, " +
                                    "backend=${imageMetrics.backend}, size=${imageMetrics.width}x${imageMetrics.height}, steps=${imageMetrics.steps}",
                            )
                        }
                        val metricsText = result.metrics?.let(::formatMetricsText) ?: ""
                        metricsLabel.text = metricsText.ifBlank { "No metrics available" }
                        metricsLabel.visibility = View.VISIBLE
                        updateProgressUI(100, "Complete. $metricsText")
                    },
                    onCancelled = { requestId, reason, phase ->
                        android.util.Log.i(
                            TAG,
                            "Image request cancelled in activity: requestId=$requestId, reason=${reason.logLabel}, phase=$phase",
                        )
                        if (!isDestroyed) {
                            updateProgressUI(0, "Cancelled: ${reason.statusLabel}")
                        }
                    },
                    onFinished = {
                        progressBar.visibility = View.GONE
                        generateButton.isEnabled = true
                    },
                ),
        )
    }

    private fun cancelGeneration() {
        controller.cancel(ImageGenerationCancellationReason.USER_CANCEL)
    }

    private fun updateProgressUI(percent: Int, status: String) {
        runOnUiThread {
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = percent == 0
            if (!progressBar.isIndeterminate) {
                progressBar.progress = percent
            }
            progressLabel.text = status
        }
    }

    private fun formatMetricsText(metrics: GenerationMetrics): String {
        val imageMetrics = metrics.imageRequestMetrics
        if (imageMetrics != null) {
            val warmLabel = if (imageMetrics.cacheHit) "warm" else "cold"
            return "Load ${formatDuration(imageMetrics.runtimeAcquireMs)} ($warmLabel), " +
                "Generate ${formatDuration(imageMetrics.generateMs)}, " +
                "Total ${formatDuration(imageMetrics.totalWallTimeMs)}"
        }
        return "Generated in ${String.format("%.1f", metrics.totalTimeSeconds)}s"
    }

    private fun formatDuration(durationMs: Long): String = String.format("%.2fs", durationMs / 1000f)

    private fun parseDimensionField(field: EditText, defaultValue: Int, label: String): Int? {
        val value = field.text.toString().ifBlank { defaultValue.toString() }.toIntOrNull()
        return if (value == null || value !in 128..1024 || value % 8 != 0) {
            field.error = "$label must be a multiple of 8 between 128 and 1024"
            field.requestFocus()
            null
        } else {
            field.error = null
            value
        }
    }

    private fun parseStepsField(): Int? {
        val value = stepsInput.text.toString().ifBlank { DEFAULT_STEPS.toString() }.toIntOrNull()
        return if (value == null || value !in 1..50) {
            stepsInput.error = "Steps must be between 1 and 50"
            stepsInput.requestFocus()
            null
        } else {
            stepsInput.error = null
            value
        }
    }

    private fun parseCfgField(): Float? {
        val value = cfgInput.text.toString().ifBlank { DEFAULT_CFG.toString() }.toFloatOrNull()
        return if (value == null || value !in 1.0f..15.0f) {
            cfgInput.error = "CFG must be between 1.0 and 15.0"
            cfgInput.requestFocus()
            null
        } else {
            cfgInput.error = null
            value
        }
    }

    private fun parseSeedField(): Long? {
        val value = seedInput.text.toString().ifBlank { DEFAULT_SEED.toString() }.toLongOrNull()
        return if (value == null || value < -1L) {
            seedInput.error = "Seed must be -1 or non-negative"
            seedInput.requestFocus()
            null
        } else {
            seedInput.error = null
            value
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (TrimMemorySupport.isRunningLow(level)) {
            android.util.Log.w(TAG, "System memory low (level=$level)")
            if (controller.isGenerating()) {
                controller.cancel(ImageGenerationCancellationReason.LOW_MEMORY)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "Generation cancelled due to low memory",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onStop() {
        android.util.Log.i(
            TAG,
            "onStop: isGenerating=${controller.isGenerating()}, requestId=${controller.currentRequestId()}, phase=${controller.currentPhaseText()}",
        )
        if (controller.isGenerating()) {
            controller.cancel(ImageGenerationCancellationReason.SCREEN_LEFT)
        }
        super.onStop()
    }

    override fun onDestroy() {
        android.util.Log.i(
            TAG,
            "onDestroy: isGenerating=${controller.isGenerating()}, requestId=${controller.currentRequestId()}, phase=${controller.currentPhaseText()}, isFinishing=$isFinishing",
        )
        if (controller.isGenerating()) {
            controller.cancel(ImageGenerationCancellationReason.SCREEN_LEFT)
        }
        super.onDestroy()
    }
}
