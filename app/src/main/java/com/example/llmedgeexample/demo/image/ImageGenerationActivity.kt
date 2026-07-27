package com.example.llmedgeexample.demo.image

import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.image.diffusion.GenerationMetrics
import io.aatricks.llmedge.model.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for image generation supporting SD 1.5 (MeinaMix), FLUX.2 Klein Bonsai,
 * SD3 Medium, MiniT2I, and Chroma Radiance.
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

    private val views by lazy(LazyThreadSafetyMode.NONE) { ImageGenerationViews.bind(this) }

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
    private val requestPreparer by lazy(LazyThreadSafetyMode.NONE) { ImageGenerationRequestPreparer() }
    private var requestPreparationJob: Job? = null
    private var lastBitmap: Bitmap? = null
    private var selectedModelOverride: ModelSpec? = null

    private val modelPickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let(::loadImportedModel)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_generation) // Use image-specific layout

        views.generateButton.text = "Generate Image"

        views.progressBar.max = 100
        views.progressBar.progress = 0
        views.progressBar.visibility = View.GONE

        views.generateButton.setOnClickListener { startGeneration() }
        views.cancelButton.setOnClickListener { cancelGeneration() }
        views.selectModelButton.setOnClickListener {
            modelPickerLauncher.launch(
                ImportedModelSupport.createPickerIntent("Select compatible image model (.gguf)"),
            )
        }
        views.clearModelButton.setOnClickListener { clearImportedModel() }
        views.upscaleButton.setOnClickListener {
            val bitmap = lastBitmap ?: return@setOnClickListener
            views.upscaleButton.isEnabled = false
            views.saveImageButton.isEnabled = false
            controller.startUpscale(
                bitmap = bitmap,
                edge = edge,
                callbacks = ImageGenerationCallbacks(
                    onProgress = ::updateProgressUI,
                    onCompleted = { },
                    onUpscaled = { upscaledBitmap ->
                        lastBitmap = upscaledBitmap
                        views.previewImage.setImageBitmap(upscaledBitmap)
                        views.upscaleButton.isEnabled = true
                        views.saveImageButton.isEnabled = true
                    },
                    onCancelled = { requestId, reason, phase ->
                        android.util.Log.i(TAG, "Image upscale cancelled: requestId=$requestId, reason=${reason.logLabel}")
                        if (!isDestroyed) {
                            updateProgressUI(0, "Cancelled: ${reason.statusLabel}")
                            views.upscaleButton.isEnabled = true
                            views.saveImageButton.isEnabled = lastBitmap != null
                        }
                    },
                    onFinished = {
                        views.progressBar.visibility = View.GONE
                        views.generateButton.isEnabled = true
                        views.upscaleButton.isEnabled = lastBitmap != null
                        views.saveImageButton.isEnabled = lastBitmap != null
                    }
                )
            )
        }

        views.saveImageButton.setOnClickListener {
            val bitmap = lastBitmap ?: return@setOnClickListener
            lifecycleScope.launch {
                val uri = saveBitmapToGallery(this@ImageGenerationActivity, bitmap, "image_${System.currentTimeMillis()}.png")
                if (uri != null) {
                    Toast.makeText(this@ImageGenerationActivity, "Saved to Gallery: $uri", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@ImageGenerationActivity, "Failed to save image", Toast.LENGTH_SHORT).show()
                }
            }
        }

        views.loraToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                views.hyperSd3Toggle.isChecked = false
                Toast.makeText(this, "Detail Tweaker LoRA Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Detail Tweaker LoRA Disabled", Toast.LENGTH_SHORT).show()
            }
        }
        views.hyperSd3Toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                views.loraToggle.isChecked = false
                views.stepsInput.setText("4")
                views.cfgInput.setText("3.0")
                Toast.makeText(this, "Hyper-SD3 enabled: 4 steps, CFG 3.0", Toast.LENGTH_SHORT).show()
            }
        }
        views.modelPresetGroup.setOnCheckedChangeListener { _, _ ->
            val preset = selectedPreset()
            views.widthInput.setText(preset.defaultWidth.toString())
            views.heightInput.setText(preset.defaultHeight.toString())
            views.stepsInput.setText(preset.defaultSteps.toString())
            views.cfgInput.setText(preset.defaultCfg.toString())
            views.loraToggle.isEnabled = preset.supportsLora
            if (!preset.supportsLora) {
                views.loraToggle.isChecked = false
            }
            views.hyperSd3Toggle.isEnabled = preset == ImageModelPreset.SD3_MEDIUM
            if (preset != ImageModelPreset.SD3_MEDIUM) {
                views.hyperSd3Toggle.isChecked = false
            }
            clearImportedModel()
        }

        views.shareLogsButton.setOnClickListener { shareLogs() }
        GenerationLogs.currentLogPathLabel()?.let { views.logPathLabel.text = it }

        // Log initial memory state
        logDemoMemoryState(TAG, "Activity created", includeGpu = true) { FileLogger.i(TAG, it) }
    }

    private fun selectedPreset(): ImageModelPreset {
        return when (views.modelPresetGroup.checkedRadioButtonId) {
            R.id.presetFlux2Bonsai -> ImageModelPreset.FLUX2_KLEIN_BONSAI
            R.id.presetSd3Medium -> ImageModelPreset.SD3_MEDIUM
            R.id.presetMiniT2i -> ImageModelPreset.MINI_T2I
            R.id.presetMiniT2iLarge -> ImageModelPreset.MINI_T2I_LARGE
            R.id.presetChromaMobile -> ImageModelPreset.CHROMA_MOBILE
            R.id.presetChromaRadiance -> ImageModelPreset.CHROMA_RADIANCE
            else -> ImageModelPreset.SD15
        }
    }

    private fun loadImportedModel(uri: Uri) {
        if (requestPreparationJob?.isActive == true || controller.isGenerating()) {
            Toast.makeText(this, "Wait for generation to finish", Toast.LENGTH_SHORT).show()
            return
        }
        val previousModel = selectedModelOverride
        val previousLabel = views.modelLabel.text
        val internalNamePrefix =
            when (selectedPreset()) {
                ImageModelPreset.SD15 -> "stable-diffusion-"
                ImageModelPreset.FLUX2_KLEIN_BONSAI -> "flux-"
                ImageModelPreset.SD3_MEDIUM -> "sd3-"
                ImageModelPreset.MINI_T2I,
                ImageModelPreset.MINI_T2I_LARGE,
                -> "minit2i-"
                ImageModelPreset.CHROMA_MOBILE,
                ImageModelPreset.CHROMA_RADIANCE,
                -> "chroma-"
            }
        views.selectModelButton.isEnabled = false
        views.generateButton.isEnabled = false
        views.modelLabel.text = "Importing model..."
        lifecycleScope.launch {
            try {
                val imported =
                    withContext(Dispatchers.IO) {
                        ImportedModelSupport.copyToAppStorage(
                            context = this@ImageGenerationActivity,
                            uri = uri,
                            internalNamePrefix = internalNamePrefix,
                        )
                    }
                selectedModelOverride = ModelSpec.localFile(imported.file)
                views.modelLabel.text = imported.displayName
                views.clearModelButton.visibility = View.VISIBLE
                FileLogger.i(TAG, "Imported compatible image model: ${imported.file.absolutePath}")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                selectedModelOverride = previousModel
                views.modelLabel.text = previousLabel
                views.clearModelButton.visibility =
                    if (previousModel == null) View.GONE else View.VISIBLE
                FileLogger.e(TAG, "Failed to import image model", t)
                Toast.makeText(
                    this@ImageGenerationActivity,
                    "Failed to import model: ${t.localizedMessage ?: "unknown error"}",
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                views.selectModelButton.isEnabled = true
                if (!controller.isGenerating() && requestPreparationJob?.isActive != true) {
                    views.generateButton.isEnabled = true
                }
            }
        }
    }

    private fun clearImportedModel() {
        if (requestPreparationJob?.isActive == true || controller.isGenerating()) {
            return
        }
        (selectedModelOverride as? ModelSpec.LocalFile)?.file?.let { file ->
            if (!ImportedModelSupport.deleteFromAppStorage(this, file)) {
                FileLogger.w(TAG, "Unable to delete imported model: ${file.absolutePath}")
            }
        }
        selectedModelOverride = null
        views.modelLabel.text = "Use selected preset model"
        views.clearModelButton.visibility = View.GONE
    }

    private fun startGeneration() {
        if (controller.isGenerating() || requestPreparationJob?.isActive == true) {
            Toast.makeText(this, "Generation already running", Toast.LENGTH_SHORT).show()
            return
        }

        val width = ImageGenerationFormSupport.parseDimensionField(views.widthInput, DEFAULT_WIDTH, "Width") ?: return
        val height = ImageGenerationFormSupport.parseDimensionField(views.heightInput, DEFAULT_HEIGHT, "Height") ?: return
        val steps = ImageGenerationFormSupport.parseStepsField(views.stepsInput, DEFAULT_STEPS) ?: return
        val cfg = ImageGenerationFormSupport.parseCfgField(views.cfgInput, DEFAULT_CFG) ?: return
        val seed = ImageGenerationFormSupport.parseSeedField(views.seedInput, DEFAULT_SEED) ?: return

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
        views.progressBar.visibility = View.VISIBLE
        views.progressBar.isIndeterminate = true
        views.generateButton.isEnabled = false
        views.selectModelButton.isEnabled = false
        views.clearModelButton.isEnabled = false
        views.upscaleButton.isEnabled = false
        views.saveImageButton.isEnabled = false
        val prompt = views.promptInput.text.toString().ifBlank { DEFAULT_PROMPT }
        val negative = views.negativePromptInput.text.toString().trim()
        val preset = selectedPreset()
        val loraRequested = views.loraToggle.isChecked && preset.supportsLora
        val hyperSd3Requested =
            views.hyperSd3Toggle.isChecked && preset == ImageModelPreset.SD3_MEDIUM
        val anyLoraRequested = loraRequested || hyperSd3Requested

        val useFlashAttn = width >= 512 && height >= 512
        val baseRequest =
            preset.buildRequest(
                prompt = prompt,
                negative = negative,
                width = width,
                height = height,
                steps = steps,
                cfg = cfg,
                seed = seed,
                flashAttention = useFlashAttn,
                modelOverride = selectedModelOverride,
                easyCacheEnabled = views.easyCacheToggle.isChecked,
            )

        requestPreparationJob =
            lifecycleScope.launch {
                try {
                    val prepared =
                        withContext(Dispatchers.IO) {
                            requestPreparer.prepare(
                                edge = edge,
                                baseRequest = baseRequest,
                                loraRequested = loraRequested,
                                hyperSd3Requested = hyperSd3Requested,
                                onStatus = { status -> updateProgressUI(0, status) },
                            )
                        }
                    if (!isActive) {
                        return@launch
                    }
                    prepared.warningMessage?.let {
                        Toast.makeText(this@ImageGenerationActivity, it, Toast.LENGTH_LONG).show()
                    }

                    logDemoMemoryState(TAG, "Before image generation", includeGpu = true) { FileLogger.i(TAG, it) }

                    val executionOverride = prepared.request.sequential?.toString() ?: "AUTO"
                    val submitLog =
                        "Submitting image request: width=${prepared.request.width}, height=${prepared.request.height}, steps=${prepared.request.steps}, " +
                            "flash=${prepared.request.flashAttention}, easyCache=${prepared.request.easyCache.enabled}, " +
                            "executionOverride=$executionOverride, nativeSequentialLoad=${prepared.request.forceSequentialLoad}, loraRequested=$anyLoraRequested, " +
                            "loraApplied=${prepared.loraApplied}, loraDir=${prepared.request.loraModelDir ?: "none"}"
                    FileLogger.i(TAG, submitLog)
                    android.util.Log.i(
                        TAG,
                        "Submitting image request: width=${prepared.request.width}, height=${prepared.request.height}, steps=${prepared.request.steps}, " +
                            "flash=${prepared.request.flashAttention}, easyCache=${prepared.request.easyCache.enabled}, " +
                            "executionOverride=$executionOverride, nativeSequentialLoad=${prepared.request.forceSequentialLoad}, loraRequested=$anyLoraRequested, " +
                            "loraApplied=${prepared.loraApplied}, loraDir=${prepared.request.loraModelDir ?: "none"}",
                    )

                    controller.start(
                        config =
                            ImageGenerationConfig(
                                request = prepared.request,
                                loraRequested = anyLoraRequested,
                            ),
                        callbacks =
                            ImageGenerationCallbacks(
                                onProgress = ::updateProgressUI,
                                onCompleted = { result ->
                                    logDemoMemoryState(TAG, "After image generation", includeGpu = true) { FileLogger.i(TAG, it) }
                                    lastBitmap = result.bitmap
                                    views.previewImage.setImageBitmap(result.bitmap)
                                    views.upscaleButton.isEnabled = true
                                    views.saveImageButton.isEnabled = true
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
                                    views.metricsLabel.text = metricsText.ifBlank { "No metrics available" }
                                    views.metricsLabel.visibility = View.VISIBLE
                                    updateProgressUI(100, "Complete. $metricsText")
                                },
                                onUpscaled = {},
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
                                    views.progressBar.visibility = View.GONE
                                    views.generateButton.isEnabled = true
                                    views.selectModelButton.isEnabled = true
                                    views.clearModelButton.isEnabled = true
                                },
                            ),
                    )
                } catch (_: CancellationException) {
                    if (!controller.isGenerating() && !isDestroyed) {
                        views.generateButton.isEnabled = true
                        updateProgressUI(0, "Cancelled: user cancelled")
                        views.progressBar.visibility = View.GONE
                    }
                } catch (t: Throwable) {
                    FileLogger.e(TAG, "Failed to prepare image request", t)
                    android.util.Log.e(TAG, "Failed to prepare image request", t)
                    if (!isDestroyed) {
                        views.progressBar.visibility = View.GONE
                        views.generateButton.isEnabled = true
                        Toast.makeText(
                            this@ImageGenerationActivity,
                            "Failed to prepare request: ${t.localizedMessage ?: "unknown error"}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                } finally {
                    requestPreparationJob = null
                    if (!controller.isGenerating() && !isDestroyed) {
                        views.selectModelButton.isEnabled = true
                        views.clearModelButton.isEnabled = true
                    }
                }
            }
    }

    private fun cancelGeneration() {
        if (requestPreparationJob?.isActive == true) {
            requestPreparationJob?.cancel(CancellationException("Image request preparation cancelled"))
            return
        }
        controller.cancel(ImageGenerationCancellationReason.USER_CANCEL)
    }

    private fun shareLogs() {
        val intent = GenerationLogs.buildShareLogsIntent(this)
        if (intent == null) {
            Toast.makeText(this, "No log file found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            startActivity(Intent.createChooser(intent, "Share Logs"))
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to share logs", e)
            val logFile = FileLogger.getCurrentLogFile()
            Toast.makeText(this, "Log file: ${logFile ?: "unavailable"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateProgressUI(percent: Int, status: String) {
        runOnUiThread {
            GenerationDemoSupport.updateProgress(views.progressBar, views.progressLabel, percent, status)
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

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (TrimMemorySupport.isRunningLow(level)) {
            android.util.Log.w(TAG, "System memory low (level=$level)")
            if (requestPreparationJob?.isActive == true) {
                requestPreparationJob?.cancel(CancellationException("Image request preparation cancelled due to low memory"))
            }
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
            "onStop: isGenerating=${controller.isGenerating()}, preparing=${requestPreparationJob?.isActive == true}, requestId=${controller.currentRequestId()}, phase=${controller.currentPhaseText()}",
        )
        requestPreparationJob?.cancel(CancellationException("Image request preparation cancelled because screen left"))
        if (controller.isGenerating()) {
            controller.cancel(ImageGenerationCancellationReason.SCREEN_LEFT)
        }
        super.onStop()
    }

    override fun onDestroy() {
        android.util.Log.i(
            TAG,
            "onDestroy: isGenerating=${controller.isGenerating()}, preparing=${requestPreparationJob?.isActive == true}, requestId=${controller.currentRequestId()}, phase=${controller.currentPhaseText()}, isFinishing=$isFinishing",
        )
        requestPreparationJob?.cancel(CancellationException("Image request preparation cancelled because activity is being destroyed"))
        if (controller.isGenerating()) {
            controller.cancel(ImageGenerationCancellationReason.SCREEN_LEFT)
        }
        super.onDestroy()
    }
}
