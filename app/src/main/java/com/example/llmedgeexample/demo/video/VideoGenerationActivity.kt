package com.example.llmedgeexample.demo.video

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Activity for video generation using Wan 2.1 and Wan 2.2 models.
 *
 * Supports:
 * - Text-to-Video (T2V) generation
 * - Image-to-Video (I2V) generation with init image
 * - LoRA weights for style transfer
 *
 * Uses sequential loading on low-memory devices (<8GB RAM):
 * 1. Load T5 encoder -> Encode prompt -> Unload T5
 * 2. Load diffusion model + VAE -> Generate frames -> Unload
 *
 * This allows video generation on devices with limited memory.
 */
class VideoGenerationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoGenerationActivity"
        private const val DEFAULT_PROMPT = "A dog running in the park"
        private const val DEFAULT_WIDTH = 512
        private const val DEFAULT_HEIGHT = 512
        private const val DEFAULT_STEPS = 30
        private const val DEFAULT_CFG = 5.0f
        private const val DEFAULT_SEED = -1L
        private const val DEFAULT_FRAMES = 9
        private const val DEFAULT_FPS = 8
    }

    private val views by lazy(LazyThreadSafetyMode.NONE) { VideoGenerationViews.bind(this) }

    private var animationJob: Job? = null
    private var initImageBitmap: Bitmap? = null
    private var generatedFrames: List<Bitmap> = emptyList()
    private var selectedLoraPath: String? = null
    private var selectedTaehvPath: String? = null
    private val edge by lazy(LazyThreadSafetyMode.NONE) {
        bindEdge(this, this, lifecycleScope, preferPerformanceMode = !isLowRamDevice())
    }
    private val generationController by lazy(LazyThreadSafetyMode.NONE) {
        VideoGenerationController(this, lifecycleScope, edge, TAG)
    }

    // Image picker result handler
    private val imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri -> loadInitImage(uri) }
                }
            }

    // LoRA file picker result handler
    private val loraPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri -> loadLoraFile(uri) }
                }
            }

    // TAEHV file picker result handler
    private val taehvPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri -> loadTaehvFile(uri) }
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_generation)

        views.progressBar.max = 100
        views.progressBar.progress = 0
        views.progressBar.visibility = View.GONE

        VideoGenerationFormSupport.bindAdapters(
            this,
            views.modelSpinner,
            views.samplerSpinner,
            views.schedulerSpinner,
        )

        views.generateButton.setOnClickListener { startGeneration() }
        views.cancelButton.setOnClickListener { cancelGeneration() }
        views.selectImageButton.setOnClickListener { selectInitImage() }
        views.clearImageButton.setOnClickListener { clearInitImage() }
        views.saveGifButton.setOnClickListener { saveAsGif() }
        views.selectLoraButton.setOnClickListener { selectLoraFile() }
        views.clearLoraButton.setOnClickListener { clearLoraFile() }
        views.selectTaehvButton.setOnClickListener { selectTaehvFile() }
        views.clearTaehvButton.setOnClickListener { clearTaehvFile() }
        views.shareLogsButton.setOnClickListener { shareLogs() }

        GenerationLogs.currentLogPathLabel()?.let { path ->
            views.logPathLabel.text = path
        }

        // Strength slider listener
        views.i2vStrengthSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        val strength = progress / 100.0f
                        views.i2vStrengthLabel.text = String.format("%.2f", strength)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
        )

        // Log initial memory state
        logDemoMemoryState(TAG, "Activity created", includeGpu = true) { FileLogger.i(TAG, it) }
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

    private fun saveAsGif() {
        if (generatedFrames.isEmpty()) {
            Toast.makeText(this, "No video frames to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val fps = views.fpsInput.text.toString().toIntOrNull() ?: DEFAULT_FPS

                views.progressLabel.text = "Saving GIF..."
                views.progressBar.visibility = View.VISIBLE
                views.progressBar.isIndeterminate = true

                val gifFile = VideoGenerationMediaSupport.saveFramesAsGif(generatedFrames, fps)
                val uri = saveFileToGallery(
                    context = this@VideoGenerationActivity,
                    file = gifFile,
                    mimeType = "image/gif",
                    relativePath = "Pictures/LLMEdge",
                    displayName = gifFile.name
                )

                views.progressBar.visibility = View.GONE
                if (uri != null) {
                    Toast.makeText(
                        applicationContext,
                        "Saved GIF to Gallery: $uri",
                        Toast.LENGTH_LONG,
                    ).show()
                    FileLogger.i(TAG, "GIF saved to Gallery: $uri")
                } else {
                    Toast.makeText(
                        applicationContext,
                        "Failed to save GIF to gallery",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to save frames", e)
                views.progressBar.visibility = View.GONE
                Toast.makeText(
                    applicationContext,
                    "Failed to save: ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun selectInitImage() {
        imagePickerLauncher.launch(VideoGenerationMediaSupport.createImagePickerIntent())
    }

    private fun loadInitImage(uri: Uri) {
        try {
            val bitmap = VideoGenerationMediaSupport.loadInitImage(this, uri)
            if (bitmap != null) {
                initImageBitmap = bitmap
                views.i2vImageLabel.text = "Image loaded (${bitmap.width}x${bitmap.height})"
                views.i2vPreviewImage.setImageBitmap(bitmap)
                views.i2vPreviewImage.visibility = View.VISIBLE
                views.clearImageButton.visibility = View.VISIBLE
            } else {
                Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load image", e)
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearInitImage() {
        initImageBitmap?.recycle()
        initImageBitmap = null
        views.i2vImageLabel.text = "No image selected"
        views.i2vPreviewImage.visibility = View.GONE
        views.i2vPreviewImage.setImageBitmap(null)
        views.clearImageButton.visibility = View.GONE
    }

    private fun selectLoraFile() {
        loraPickerLauncher.launch(
            VideoGenerationMediaSupport.createSafetensorPickerIntent("Select LoRA (.safetensors)"),
        )
    }

    private fun loadLoraFile(uri: Uri) {
        try {
            val loraFile = VideoGenerationMediaSupport.loadLoraFile(this, uri)
            selectedLoraPath = loraFile.selectionPath
            views.loraLabel.text = loraFile.file.name
            views.clearLoraButton.visibility = View.VISIBLE
            FileLogger.i(TAG, "LoRA loaded: ${loraFile.file.absolutePath}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load LoRA file", e)
            Toast.makeText(this, "Failed to load LoRA: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearLoraFile() {
        selectedLoraPath = null
        views.loraLabel.text = "No LoRA selected"
        views.clearLoraButton.visibility = View.GONE
    }

    private fun selectTaehvFile() {
        taehvPickerLauncher.launch(
            VideoGenerationMediaSupport.createSafetensorPickerIntent("Select TAEHV (.safetensors)"),
        )
    }

    private fun loadTaehvFile(uri: Uri) {
        try {
            val taehvFile = VideoGenerationMediaSupport.loadTaehvFile(this, uri)
            selectedTaehvPath = taehvFile.selectionPath
            views.taehvLabel.text = taehvFile.file.name
            views.clearTaehvButton.visibility = View.VISIBLE
            FileLogger.i(TAG, "TAEHV loaded: $selectedTaehvPath")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load TAEHV file", e)
            Toast.makeText(this, "Failed to load TAEHV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearTaehvFile() {
        selectedTaehvPath = null
        views.taehvLabel.text = "No TAEHV selected"
        views.clearTaehvButton.visibility = View.GONE
    }

    private fun startGeneration() {
        if (generationController.isGenerating()) {
            Toast.makeText(this, R.string.video_status_generation_running, Toast.LENGTH_SHORT)
                    .show()
            return
        }

        // Stop any previous animation
        animationJob?.cancel()

        val config = buildGenerationConfig() ?: return

        val availMemMB = availableMemoryMb()
        if (availMemMB < 1500) {
            Toast.makeText(
                            this,
                            "Low memory (${availMemMB}MB). Close other apps for better results.",
                            Toast.LENGTH_LONG
                    )
                    .show()
        }

        updateProgressUI(0, "Preparing parameters...")
        views.generateButton.isEnabled = false
        generationController.start(
                config,
                VideoGenerationCallbacks(
                        onProgress = ::updateProgressUI,
                        onCompleted = { result ->
                            if (result.frames.isNotEmpty()) {
                                views.metricsLabel.text =
                                        result.metricsSummary ?: "Generated ${result.frames.size} frames"
                                views.metricsLabel.visibility = View.VISIBLE
                                generatedFrames = result.frames
                                views.saveGifButton.visibility = View.VISIBLE
                                startPreviewAnimation(result.frames, result.fps)
                            }
                        },
                        onFinished = {
                            views.progressBar.visibility = View.GONE
                            views.generateButton.isEnabled = true
                        },
                ),
        )
    }

    private fun cancelGeneration() {
        animationJob?.cancel()
        generationController.cancel()
        updateProgressUI(0, getString(R.string.video_status_cancelled))
    }

    private fun startPreviewAnimation(frames: List<Bitmap>, fps: Int) {
        animationJob?.cancel()
        if (frames.isEmpty()) {
            views.previewImage.setImageBitmap(null)
            return
        }

        views.previewImage.setImageBitmap(frames.first())
        val frameDuration = 1000L / fps
        animationJob =
                lifecycleScope.launch {
                    while (true) {
                        frames.forEach { frame ->
                            views.previewImage.setImageBitmap(frame)
                            kotlinx.coroutines.delay(frameDuration)
                        }
                    }
                }
    }

    private fun updateProgressUI(percent: Int, status: String) {
        runOnUiThread {
            GenerationDemoSupport.updateProgress(views.progressBar, views.progressLabel, percent, status)
        }
    }

    private fun buildGenerationConfig(): VideoGenerationConfig? {
        val width = VideoGenerationFormSupport.parseDimensionField(views.widthInput, DEFAULT_WIDTH, "Width") ?: return null
        val height = VideoGenerationFormSupport.parseDimensionField(views.heightInput, DEFAULT_HEIGHT, "Height") ?: return null
        val framesCount = VideoGenerationFormSupport.parseFramesField(views.framesInput, DEFAULT_FRAMES) ?: return null
        val fps = VideoGenerationFormSupport.parseFpsField(views.fpsInput, DEFAULT_FPS) ?: return null
        val steps = VideoGenerationFormSupport.parseStepsField(views.stepsInput, DEFAULT_STEPS) ?: return null
        val cfg = VideoGenerationFormSupport.parseCfgField(views.cfgInput, DEFAULT_CFG) ?: return null
        val seed = VideoGenerationFormSupport.parseSeedField(views.seedInput, DEFAULT_SEED) ?: return null
        val flowShift = VideoGenerationFormSupport.parseFlowShiftField(views.flowShiftInput) ?: return null
        val modelPreset =
            VideoGenerationFormSupport.selectedModelPreset(views.modelSpinner.selectedItemPosition)

        return VideoGenerationConfig(
            prompt = views.promptInput.text.toString().ifBlank { DEFAULT_PROMPT },
            negative = views.negativePromptInput.text.toString().trim(),
            width = width,
            height = height,
            frames = framesCount,
            fps = fps,
            steps = steps,
            cfgScale = cfg,
            seed = seed,
            flowShift = flowShift,
            model = modelPreset.model,
            vae = modelPreset.vae,
            textEncoder = modelPreset.textEncoder,
            sampleMethod = VideoGenerationFormSupport.selectedSampleMethod(views.samplerSpinner.selectedItemPosition),
            scheduler = VideoGenerationFormSupport.selectedScheduler(views.schedulerSpinner.selectedItemPosition),
            loraDirectory = selectedLoraPath,
            taehvPath = selectedTaehvPath,
            initImage = initImageBitmap,
            initImageStrength = views.i2vStrengthSeekBar.progress / 100.0f,
            defaultLoraDirectory = getExternalFilesDir("loras")?.absolutePath,
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (TrimMemorySupport.isRunningLow(level)) {
            FileLogger.w(TAG, "System memory low (level=$level), cancelling if active")
            if (generationController.isGenerating()) {
                cancelGeneration()
                runOnUiThread {
                    Toast.makeText(
                                    this,
                                    "Generation cancelled due to low memory",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        generationController.cancel()
        animationJob?.cancel()
        initImageBitmap?.recycle()
        initImageBitmap = null
    }
}
