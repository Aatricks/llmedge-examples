package com.example.llmedgeexample

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.aatricks.llmedge.image.diffusion.SampleMethod
import io.aatricks.llmedge.image.diffusion.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity for video generation using Wan 2.1 model.
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

    private val promptInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoPromptInput) }
    private val widthInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoWidthInput) }
    private val heightInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoHeightInput) }
    private val framesInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoFramesInput) }
    private val fpsInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoFpsInput) }
    private val stepsInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoStepsInput) }
    private val cfgInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoCfgInput) }
    private val seedInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoSeedInput) }
    private val flowShiftInput: EditText by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoFlowShiftInput) }
    private val samplerSpinner: Spinner by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.samplerSpinner) }
    private val schedulerSpinner: Spinner by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.schedulerSpinner) }
    private val selectLoraButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnSelectLora) }
    private val loraLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.loraLabel) }
    private val clearLoraButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnClearLora) }
    private val selectTaehvButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnSelectTaehv) }
    private val taehvLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.taehvLabel) }
    private val clearTaehvButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnClearTaehv) }
    private val generateButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnGenerateVideo) }
    private val cancelButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnCancelVideo) }
    private val selectImageButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnSelectImage) }
    private val clearImageButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnClearImage) }
    private val saveGifButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnSaveGif) }
    private val shareLogsButton: Button by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnShareLogs) }
    private val logPathLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.logPathLabel) }
    private val progressBar: ProgressBar by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoProgressBar) }
    private val progressLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoProgressLabel) }
    private val previewImage: ImageView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoPreview) }
    private val metricsLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.videoMetricsLabel) }
    private val i2vImageLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.i2vImageLabel) }
    private val i2vPreviewImage: ImageView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.i2vPreviewImage) }
    private val i2vStrengthSeekBar: SeekBar by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.i2vStrengthSeekBar) }
    private val i2vStrengthLabel: TextView by
            lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.i2vStrengthLabel) }

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

        progressBar.max = 100
        progressBar.progress = 0
        progressBar.visibility = View.GONE

        // Initialize sampler spinner - show recommended samplers for Wan first
        val recommendedSamplers = SampleMethod.WAN_RECOMMENDED
        val otherSamplers =
                SampleMethod.values().filter { it !in recommendedSamplers }
        val orderedSamplers = recommendedSamplers + otherSamplers
        val samplerNames =
                orderedSamplers.map {
                    val name = it.name.replace("_", " ")
                    if (it in recommendedSamplers) "$name ★" else name
                }
        samplerSpinner.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, samplerNames)

        // Initialize scheduler spinner with user-friendly names
        val schedulerNames = Scheduler.values().map { it.name.replace("_", " ") }
        schedulerSpinner.adapter =
                ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, schedulerNames)

        generateButton.setOnClickListener { startGeneration() }
        cancelButton.setOnClickListener { cancelGeneration() }
        selectImageButton.setOnClickListener { selectInitImage() }
        clearImageButton.setOnClickListener { clearInitImage() }
        saveGifButton.setOnClickListener { saveAsGif() }
        selectLoraButton.setOnClickListener { selectLoraFile() }
        clearLoraButton.setOnClickListener { clearLoraFile() }
        selectTaehvButton.setOnClickListener { selectTaehvFile() }
        clearTaehvButton.setOnClickListener { clearTaehvFile() }
        shareLogsButton.setOnClickListener { shareLogs() }

        // Show log file path
        FileLogger.getCurrentLogFile()?.let { path ->
            logPathLabel.text = path.substringAfterLast("/Android/")
        }

        // Strength slider listener
        i2vStrengthSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                    ) {
                        val strength = progress / 100.0f
                        i2vStrengthLabel.text = String.format("%.2f", strength)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
        )

        // Log initial memory state
        logDemoMemoryState(TAG, "Activity created", includeGpu = true) { FileLogger.i(TAG, it) }
    }

    private fun shareLogs() {
        FileLogger.flush() // Ensure all pending logs are written

        val logFile = FileLogger.getCurrentLogFile()?.let { java.io.File(it) }
        if (logFile == null || !logFile.exists()) {
            Toast.makeText(this, "No log file found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri =
                    androidx.core.content.FileProvider.getUriForFile(
                            this,
                            "$packageName.fileprovider",
                            logFile
                    )

            val intent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, "LLMEdge Logs")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
            startActivity(Intent.createChooser(intent, "Share Logs"))
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to share logs", e)
            // Fallback: show the path
            Toast.makeText(this, "Log file: ${logFile.absolutePath}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAsGif() {
        if (generatedFrames.isEmpty()) {
            Toast.makeText(this, "No video frames to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fps = fpsInput.text.toString().toIntOrNull() ?: DEFAULT_FPS

                // Save to Downloads/LLMEdge folder
                val downloadsDir =
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                        )
                val outputDir = java.io.File(downloadsDir, "LLMEdge")
                outputDir.mkdirs()

                val timestamp =
                        java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                .format(java.util.Date())

                withContext(Dispatchers.Main) {
                    progressLabel.text = "Saving GIF..."
                    progressBar.visibility = View.VISIBLE
                    progressBar.isIndeterminate = true
                }

                val gifFile = java.io.File(outputDir, "video_${timestamp}.gif")
                java.io.FileOutputStream(gifFile).use { fos ->
                    io.aatricks.llmedge.vision.ImageUtils.createAnimatedGif(
                            frames = generatedFrames,
                            delayMs = 1000 / fps,
                            output = fos,
                            loop = 0
                    )
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                                    applicationContext,
                                    "Saved GIF to: ${gifFile.absolutePath}",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }

                FileLogger.i(TAG, "GIF saved to: ${gifFile.absolutePath}")
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to save frames", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                                    applicationContext,
                                    "Failed to save: ${e.message}",
                                    Toast.LENGTH_LONG
                            )
                            .show()
                }
            }
        }
    }

    private fun selectInitImage() {
        val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
        imagePickerLauncher.launch(Intent.createChooser(intent, "Select Init Image"))
    }

    private fun loadInitImage(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    initImageBitmap = bitmap
                    i2vImageLabel.text = "Image loaded (${bitmap.width}x${bitmap.height})"
                    i2vPreviewImage.setImageBitmap(bitmap)
                    i2vPreviewImage.visibility = View.VISIBLE
                    clearImageButton.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load image", e)
            Toast.makeText(this, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearInitImage() {
        initImageBitmap?.recycle()
        initImageBitmap = null
        i2vImageLabel.text = "No image selected"
        i2vPreviewImage.visibility = View.GONE
        i2vPreviewImage.setImageBitmap(null)
        clearImageButton.visibility = View.GONE
    }

    private fun selectLoraFile() {
        val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
                }
        loraPickerLauncher.launch(Intent.createChooser(intent, "Select LoRA (.safetensors)"))
    }

    private fun loadLoraFile(uri: Uri) {
        try {
            val loraFile =
                    copyOpenableToCache(
                            uri = uri,
                            subdirectory = "loras",
                            fallbackFileName =
                                    "lora_${System.currentTimeMillis()}.safetensors",
                            requiredSuffix = ".safetensors"
                    )
            selectedLoraPath = loraFile.parentFile?.absolutePath
            loraLabel.text = loraFile.name
            clearLoraButton.visibility = View.VISIBLE
            FileLogger.i(TAG, "LoRA loaded: ${loraFile.absolutePath}")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load LoRA file", e)
            Toast.makeText(this, "Failed to load LoRA: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearLoraFile() {
        selectedLoraPath = null
        loraLabel.text = "No LoRA selected"
        clearLoraButton.visibility = View.GONE
    }

    private fun selectTaehvFile() {
        val intent =
                Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
                }
        taehvPickerLauncher.launch(Intent.createChooser(intent, "Select TAEHV (.safetensors)"))
    }

    private fun loadTaehvFile(uri: Uri) {
        try {
            val taehvFile =
                    copyOpenableToCache(
                            uri = uri,
                            subdirectory = "taehv",
                            fallbackFileName =
                                    "taehv_${System.currentTimeMillis()}.safetensors",
                            requiredSuffix = ".safetensors"
                    )
            selectedTaehvPath = taehvFile.absolutePath
            taehvLabel.text = taehvFile.name
            clearTaehvButton.visibility = View.VISIBLE
            FileLogger.i(TAG, "TAEHV loaded: $selectedTaehvPath")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load TAEHV file", e)
            Toast.makeText(this, "Failed to load TAEHV: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearTaehvFile() {
        selectedTaehvPath = null
        taehvLabel.text = "No TAEHV selected"
        clearTaehvButton.visibility = View.GONE
    }

    private fun startGeneration() {
        if (generationController.isGenerating()) {
            Toast.makeText(this, R.string.video_status_generation_running, Toast.LENGTH_SHORT)
                    .show()
            return
        }

        // Stop any previous animation
        animationJob?.cancel()

        val width = parseDimensionField(widthInput, DEFAULT_WIDTH, "Width") ?: return
        val height = parseDimensionField(heightInput, DEFAULT_HEIGHT, "Height") ?: return
        val framesCount = parseFramesField() ?: return
        val fps = parseFpsField() ?: return
        val steps = parseStepsField() ?: return
        val cfg = parseCfgField() ?: return
        val seed = parseSeedField() ?: return
        val flowShift = parseFlowShiftField() ?: return

        val recommendedSamplers = SampleMethod.WAN_RECOMMENDED
        val otherSamplers =
                SampleMethod.values().filter { it !in recommendedSamplers }
        val orderedSamplers = recommendedSamplers + otherSamplers
        val selectedSampleMethod = orderedSamplers[samplerSpinner.selectedItemPosition]
        val selectedScheduler =
                Scheduler.values()[schedulerSpinner.selectedItemPosition]

        val i2vStrength = i2vStrengthSeekBar.progress / 100.0f
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
        generateButton.isEnabled = false
        generationController.start(
                VideoGenerationConfig(
                        prompt = promptInput.text.toString().ifBlank { DEFAULT_PROMPT },
                        width = width,
                        height = height,
                        frames = framesCount,
                        fps = fps,
                        steps = steps,
                        cfgScale = cfg,
                        seed = seed,
                        flowShift = flowShift,
                        sampleMethod = selectedSampleMethod,
                        scheduler = selectedScheduler,
                        loraDirectory = selectedLoraPath,
                        taehvPath = selectedTaehvPath,
                        initImage = initImageBitmap,
                        initImageStrength = i2vStrength,
                        defaultLoraDirectory = getExternalFilesDir("loras")?.absolutePath,
                ),
                VideoGenerationCallbacks(
                        onProgress = ::updateProgressUI,
                        onCompleted = { result ->
                            if (result.frames.isNotEmpty()) {
                                metricsLabel.text =
                                        result.metricsSummary ?: "Generated ${result.frames.size} frames"
                                metricsLabel.visibility = View.VISIBLE
                                generatedFrames = result.frames
                                saveGifButton.visibility = View.VISIBLE
                                startPreviewAnimation(result.frames, result.fps)
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
        animationJob?.cancel()
        generationController.cancel()
        updateProgressUI(0, getString(R.string.video_status_cancelled))
    }

    private fun startPreviewAnimation(frames: List<Bitmap>, fps: Int) {
        animationJob?.cancel()
        if (frames.isEmpty()) {
            previewImage.setImageBitmap(null)
            return
        }

        previewImage.setImageBitmap(frames.first())
        val frameDuration = 1000L / fps
        animationJob =
                lifecycleScope.launch {
                    while (true) {
                        frames.forEach { frame ->
                            previewImage.setImageBitmap(frame)
                            kotlinx.coroutines.delay(frameDuration)
                        }
                    }
                }
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
        return if (value == null || value !in 256..960 || value % 64 != 0) {
            field.error = "$label must be a multiple of 64 between 256 and 960"
            field.requestFocus()
            null
        } else {
            field.error = null
            value
        }
    }

    private fun parseFramesField(): Int? {
        val value = framesInput.text.toString().ifBlank { DEFAULT_FRAMES.toString() }.toIntOrNull()
        return if (value == null || value !in 4..64) {
            framesInput.error = "Frames must be between 4 and 64"
            framesInput.requestFocus()
            null
        } else {
            framesInput.error = null
            value
        }
    }

    private fun parseFpsField(): Int? {
        val value = fpsInput.text.toString().ifBlank { DEFAULT_FPS.toString() }.toIntOrNull()
        return if (value == null || value !in 1..30) {
            fpsInput.error = "FPS must be between 1 and 30"
            fpsInput.requestFocus()
            null
        } else {
            fpsInput.error = null
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

    private fun parseFlowShiftField(): Float? {
        val raw = flowShiftInput.text.toString().trim()
        if (raw.isBlank()) {
            flowShiftInput.error = null
            return Float.POSITIVE_INFINITY
        }
        val value = raw.toFloatOrNull()
        return if (value == null || value <= 0f) {
            flowShiftInput.error = "Flow shift must be greater than 0"
            flowShiftInput.requestFocus()
            null
        } else {
            flowShiftInput.error = null
            value
        }
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
