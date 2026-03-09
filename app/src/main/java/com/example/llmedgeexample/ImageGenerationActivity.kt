package com.example.llmedgeexample

import android.app.ActivityManager
import android.content.Context
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
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.ImageGenerationRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        private const val BYTES_IN_MB = 1024L * 1024L
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

    private var generationJob: Job? = null
    private val edge by lazy(LazyThreadSafetyMode.NONE) {
        bindEdge(this, this, lifecycleScope, preferPerformanceMode = true)
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
        logMemoryState("Activity created")
    }

    private fun startGeneration() {
        if (generationJob?.isActive == true) {
            Toast.makeText(this, "Generation already running", Toast.LENGTH_SHORT).show()
            return
        }

        val width = parseDimensionField(widthInput, DEFAULT_WIDTH, "Width") ?: return
        val height = parseDimensionField(heightInput, DEFAULT_HEIGHT, "Height") ?: return
        val steps = parseStepsField() ?: return
        val cfg = parseCfgField() ?: return
        val seed = parseSeedField() ?: return

        // Check available memory
        val availMemMB = getAvailableMemoryMB()
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

        // Use Dispatchers.IO for native JNI operations - it has more threads for blocking operations
        // Dispatchers.Default is CPU-bound and has limited parallelism (core count)
        generationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                var prompt = promptInput.text.toString().ifBlank { DEFAULT_PROMPT }
                var loraModelDir: String? = null
                
                if (loraToggle.isChecked) {
                    updateProgressUI(0, "Checking LoRA model...")
                    try {
                        val result = io.aatricks.llmedge.huggingface.HuggingFaceHub.ensureRepoFileOnDisk(
                            context = applicationContext,
                            modelId = "imagepipeline/Detail-Tweaker-LoRA-SD1.5",
                            filename = null, // Auto-detect largest safetensors
                            preferSystemDownloader = true,
                            onProgress = { downloaded, total ->
                                val percent = if (total != null && total > 0) (downloaded * 100 / total).toInt() else 0
                                updateProgressUI(0, "Downloading LoRA: $percent%")
                            }
                        )
                        loraModelDir = requireNotNull(result.file.parentFile) {
                            "Downloaded LoRA file is missing a parent directory"
                        }.absolutePath
                        val loraName = result.file.nameWithoutExtension
                        prompt += " <lora:$loraName:1.0>"
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Failed to download LoRA", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ImageGenerationActivity, "Failed to download LoRA: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                
                // Log memory before generation
                logMemoryState("Before image generation")

                updateProgressUI(0, "Preparing...")

                val useFlashAttn = width >= 512 && height >= 512

                val loraApplyMode = io.aatricks.llmedge.StableDiffusion.LoraApplyMode.AUTO

                val params = ImageGenerationRequest(
                    prompt = prompt,
                    width = width,
                    height = height,
                    steps = steps,
                    cfgScale = cfg,
                    seed = seed,
                    flashAttention = useFlashAttn,
                    forceSequentialLoad = false,
                    loraModelDir = loraModelDir,
                    loraApplyMode = loraApplyMode
                )

                updateProgressUI(0, "Generating image...")
                val bitmap = edge.image.generate(params)

                // Log memory after generation
                logMemoryState("After image generation")

                withContext(Dispatchers.Main) {
                    previewImage.setImageBitmap(bitmap)
                }

                // Show metrics
                // Show metrics in the dedicated metrics label and progress status
                val metrics = edge.image.getLastGenerationMetrics()
                withContext(Dispatchers.Main) {
                    val metricsText = metrics?.let {
                        "Generated in ${String.format("%.1f", it.totalTimeSeconds)}s"
                    } ?: ""
                    metricsLabel.text = metricsText.ifBlank { "No metrics available" }
                    metricsLabel.visibility = View.VISIBLE
                    updateProgressUI(100, "Complete. $metricsText")
                }
            } catch (cancelled: CancellationException) {
                updateProgressUI(0, "Cancelled")
            } catch (oom: OutOfMemoryError) {
                android.util.Log.e(TAG, "Out of memory", oom)
                logMemoryState("OOM error")
                updateProgressUI(0, "Out of memory. Close other apps and try again.")
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "Failed", t)
                updateProgressUI(0, "Failed: ${t.localizedMessage}")
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    generateButton.isEnabled = true
                    generationJob = null
                }
            }
        }
    }

    private fun cancelGeneration() {
        generationJob?.cancel()
        edge.image.cancelGeneration()
        updateProgressUI(0, "Cancelled")
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
            if (generationJob?.isActive == true) {
                cancelGeneration()
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

    override fun onDestroy() {
        super.onDestroy()
        generationJob?.cancel()
    }

    private fun getAvailableMemoryMB(): Long {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / BYTES_IN_MB
    }

    private fun logMemoryState(phase: String) {
        val runtime = Runtime.getRuntime()
        val heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / BYTES_IN_MB
        val heapMax = runtime.maxMemory() / BYTES_IN_MB

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val systemAvail = memoryInfo.availMem / BYTES_IN_MB
        val systemTotal = memoryInfo.totalMem / BYTES_IN_MB

        android.util.Log.i(TAG, "=== Memory: $phase ===")
        android.util.Log.i(TAG, "  Heap: ${heapUsed}MB / ${heapMax}MB max")
        android.util.Log.i(TAG, "  System: ${systemAvail}MB / ${systemTotal}MB total")

        // Log Vulkan memory if available
        LLMEdge.getVulkanDeviceInfo()?.let { vulkan ->
            android.util.Log.i(TAG, "  Vulkan: ${vulkan.freeMemoryMB}MB / ${vulkan.totalMemoryMB}MB")
        }
    }
}
