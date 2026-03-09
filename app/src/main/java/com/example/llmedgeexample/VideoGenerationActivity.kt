package com.example.llmedgeexample

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
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
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.image.GenerationStreamEvent
import io.aatricks.llmedge.image.VideoGenerationRequest
import io.aatricks.llmedge.model.ModelSpec
import io.aatricks.llmedge.StableDiffusion
import io.aatricks.llmedge.EasyCacheParams
import io.aatricks.llmedge.LoraApplyMode
import io.aatricks.llmedge.SampleMethod
import io.aatricks.llmedge.Scheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
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
        private const val BYTES_IN_MB = 1024L * 1024L
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

    private var generationJob: Job? = null
    private var animationJob: Job? = null
    private var initImageBitmap: Bitmap? = null
    private var generatedFrames: List<Bitmap> = emptyList()
    private var selectedLoraPath: String? = null
    private var selectedTaehvPath: String? = null
    private val edge by lazy(LazyThreadSafetyMode.NONE) {
        bindEdge(this, this, lifecycleScope, preferPerformanceMode = !isLowMemoryDevice())
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
        logMemoryState("Activity created")
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
            // Copy the file to the app's cache directory for native access
            val fileName =
                    getFileNameFromUri(uri) ?: "lora_${System.currentTimeMillis()}.safetensors"
            if (!fileName.endsWith(".safetensors")) {
                Toast.makeText(this, "Please select a .safetensors file", Toast.LENGTH_SHORT).show()
                return
            }

            val loraDir = java.io.File(cacheDir, "loras")
            loraDir.mkdirs()
            val loraFile = java.io.File(loraDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                loraFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }

            selectedLoraPath = loraDir.absolutePath
            loraLabel.text = fileName
            clearLoraButton.visibility = View.VISIBLE
            FileLogger.i(TAG, "LoRA loaded: $selectedLoraPath/$fileName")
        } catch (e: Exception) {
            FileLogger.e(TAG, "Failed to load LoRA file", e)
            Toast.makeText(this, "Failed to load LoRA: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex =
                            cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
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
            // Copy the file to the app's cache directory for native access
            val fileName =
                    getFileNameFromUri(uri) ?: "taehv_${System.currentTimeMillis()}.safetensors"
            if (!fileName.endsWith(".safetensors")) {
                Toast.makeText(this, "Please select a .safetensors file", Toast.LENGTH_SHORT).show()
                return
            }

            val taehvDir = java.io.File(cacheDir, "taehv")
            taehvDir.mkdirs()
            val taehvFile = java.io.File(taehvDir, fileName)

            contentResolver.openInputStream(uri)?.use { inputStream ->
                taehvFile.outputStream().use { outputStream -> inputStream.copyTo(outputStream) }
            }

            selectedTaehvPath = taehvFile.absolutePath
            taehvLabel.text = fileName
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
        if (generationJob?.isActive == true) {
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

        // Get sampler and scheduler from spinners (using the same ordered list as initialization)
        val recommendedSamplers = SampleMethod.WAN_RECOMMENDED
        val otherSamplers =
                SampleMethod.values().filter { it !in recommendedSamplers }
        val orderedSamplers = recommendedSamplers + otherSamplers
        val selectedSampleMethod = orderedSamplers[samplerSpinner.selectedItemPosition]
        val selectedScheduler =
                Scheduler.values()[schedulerSpinner.selectedItemPosition]

        // Get LoRA path from file selector
        val loraDir = selectedLoraPath
        val taehvPath = selectedTaehvPath

        // Get I2V strength
        val i2vStrength = i2vStrengthSeekBar.progress / 100.0f

        // Calculate actual frames that will be generated (Wan formula)
        val actualFrames = (framesCount - 1) / 4 * 4 + 1

        // Check if we have enough memory
        val isLowMem = isLowMemoryDevice()
        val availMemMB = getAvailableMemoryMB()

        FileLogger.separator("Video Generation Starting")
        FileLogger.i(TAG, "Parameters:")
        FileLogger.i(
                TAG,
                "  Size: ${width}x${height}, Frames: $framesCount (actual: $actualFrames), Steps: $steps"
        )
        FileLogger.i(TAG, "  CFG: $cfg, Seed: $seed, FlowShift: $flowShift")
        FileLogger.i(TAG, "  Sampler: $selectedSampleMethod, Scheduler: $selectedScheduler")
        FileLogger.i(TAG, "  LoRA: ${loraDir ?: "none"}")
        FileLogger.i(TAG, "  TAEHV: ${taehvPath ?: "none"}")
        FileLogger.i(TAG, "  I2V: ${initImageBitmap != null}, Strength: $i2vStrength")
        FileLogger.i(TAG, "  Memory: isLowMem=$isLowMem, available=${availMemMB}MB")

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

        // Use Dispatchers.IO for native JNI operations - it has more threads for blocking
        // operations
        // Dispatchers.Default is CPU-bound and has limited parallelism (core count)
        generationJob =
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val prompt = promptInput.text.toString().ifBlank { DEFAULT_PROMPT }

                        // Log memory before generation
                        FileLogger.i(TAG, "Logging memory state before generation...")
                        logMemoryState("Before video generation")
                        FileLogger.i(TAG, "Memory state logged.")

                        FileLogger.i(TAG, "Preparing init image (if any)...")
                        val resizedInitImage =
                                initImageBitmap?.let { bmp ->
                                    if (bmp.width != width || bmp.height != height) {
                                        Bitmap.createScaledBitmap(bmp, width, height, true)
                                    } else {
                                        bmp
                                    }
                                }
                        FileLogger.i(TAG, "Init image preparation done. hasInitImage=${resizedInitImage != null}")

                        // Use sequential loading on low-memory devices (auto-detected)
                        // Width must be between 256-960 for Wan 2.1
                        FileLogger.i(TAG, "Constructing VideoGenerationRequest...")
                        val params =
                                VideoGenerationRequest(
                                        prompt = prompt,
                                        width = width,
                                        height = height,
                                        videoFrames = framesCount,
                                        steps = steps,
                                        cfgScale = cfg,
                                        seed = seed,
                                        flowShift = flowShift,
                                        flashAttention = true,
                                        forceSequentialLoad =
                                                true, // Always use sequential for safety
                                        sampleMethod = selectedSampleMethod,
                                        scheduler = selectedScheduler,
                                        loraModelDir = loraDir
                                                        ?: getExternalFilesDir("loras")
                                                                ?.absolutePath,
                                        loraApplyMode = LoraApplyMode.AUTO,
                                        taehv = taehvPath?.let(ModelSpec::localFile),
                                        initImage = resizedInitImage,
                                        strength = i2vStrength,
                                        easyCache =
                                                EasyCacheParams(
                                                        enabled = true,
                                                        reuseThreshold = 0.2f,
                                                        startPercent = 0.15f,
                                                        endPercent = 0.95f
                                                )
                                )
                        FileLogger.i(TAG, "VideoGenerationRequest constructed.")

                        if (resizedInitImage != null) {
                            FileLogger.i(
                                    TAG,
                                    "I2V mode: using init image ${resizedInitImage.width}x${resizedInitImage.height} with strength $i2vStrength"
                            )
                        }

                        updateProgressUI(0, "Preparing model...")
                        FileLogger.i(TAG, "Calling edge.image.generateVideo...")

                        var frames: List<Bitmap> = emptyList()
                        edge.image.generateVideo(params).collect { event ->
                            when (event) {
                                is GenerationStreamEvent.Progress -> {
                                    val step = event.update
                                    val status =
                                            if (step.total > 0) "${step.message} (${step.current}/${step.total})"
                                            else step.message
                                    updateProgressUI(0, status)
                                }
                                is GenerationStreamEvent.Completed -> {
                                    frames = event.frames
                                }
                            }
                        }
                        FileLogger.i(TAG, "edge.image.generateVideo returned ${frames.size} frames.")

                        // Log memory after generation
                        logMemoryState("After video generation")

                        if (frames.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                // Show metrics if available
                                val metrics = edge.image.getLastGenerationMetrics()
                                metrics?.let {
                                    metricsLabel.text =
                                            "Generated ${frames.size} frames in ${String.format("%.1f", it.totalTimeSeconds)}s"
                                    metricsLabel.visibility = View.VISIBLE
                                }

                                // Store frames for GIF export
                                generatedFrames = frames
                                saveGifButton.visibility = View.VISIBLE

                                // Start animation
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
                        }
                        withContext(Dispatchers.Main) {
                            updateProgressUI(
                                    100,
                                    getString(R.string.video_status_complete, frames.size)
                            )
                        }
                    } catch (cancelled: CancellationException) {
                        updateProgressUI(0, getString(R.string.video_status_cancelled))
                    } catch (oom: OutOfMemoryError) {
                        FileLogger.e(TAG, "Out of memory during generation", oom)
                        logMemoryState("OOM error")
                        updateProgressUI(
                                0,
                                getString(
                                        R.string.video_status_failed,
                                        "Out of memory. Close other apps and try again."
                                )
                        )
                    } catch (t: Throwable) {
                        FileLogger.e(TAG, "Failed during generation", t)
                        val message = t.localizedMessage ?: "error"
                        val finalMessage =
                                if (message.contains(
                                                "Failed to initialize Stable Diffusion context"
                                        ) && selectedTaehvPath != null
                                ) {
                                    "$message\n\nTip: You selected a custom TAEHV. Ensure it is compatible with Wan2.1 (e.g. taew2_1.safetensors). Incompatible TAEHVs (like SDXL's) will cause this error."
                                } else {
                                    message
                                }
                        updateProgressUI(0, getString(R.string.video_status_failed, finalMessage))
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
        animationJob?.cancel()
        edge.image.cancelGeneration()
        updateProgressUI(0, getString(R.string.video_status_cancelled))
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
            if (generationJob?.isActive == true) {
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
        generationJob?.cancel()
        initImageBitmap?.recycle()
        initImageBitmap = null
    }

    private fun isLowMemoryDevice(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamGB = memInfo.totalMem / (1024L * 1024L * 1024L)
        return totalRamGB < 8
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

        FileLogger.i(TAG, "=== Memory: $phase ===")
        FileLogger.i(TAG, "  Heap: ${heapUsed}MB / ${heapMax}MB max")
        FileLogger.i(TAG, "  System: ${systemAvail}MB / ${systemTotal}MB total")

        // Log Vulkan memory if available
        LLMEdge.getVulkanDeviceInfo()?.let { vulkan ->
            FileLogger.i(TAG, "  Vulkan: ${vulkan.freeMemoryMB}MB / ${vulkan.totalMemoryMB}MB")
        }
    }
}
