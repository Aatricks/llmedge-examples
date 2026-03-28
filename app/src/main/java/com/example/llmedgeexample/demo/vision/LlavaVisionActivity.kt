package com.example.llmedgeexample.demo.vision

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.llmedgeexample.R
import com.example.llmedgeexample.common.*
import io.aatricks.llmedge.LLMEdge
import io.aatricks.llmedge.vision.ImageSource
import io.aatricks.llmedge.vision.ImageUtils
import io.aatricks.llmedge.vision.LocalImageDescriber
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class LlavaVisionActivity : AppCompatActivity() {
    private companion object {
        private const val TAG = "LlavaVisionActivity"
        private const val IMAGE_MAX_DIMENSION = 1024
        private const val CAPTURE_MAX_DIMENSION = 1600
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val edge: LLMEdge by lazy(LazyThreadSafetyMode.NONE) {
        bindEdge(this, this, scope, preferPerformanceMode = false)
    }

    private val btnPick: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnPickImage) }
    private val btnTake: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnTakePicture) }
    private val btnRun: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnRun) }
    private val btnDescribeLocal: Button by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.btnDescribeLocal) }
    private val etPrompt: EditText by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.etPrompt) }
    private val switchOcrAssist: Switch by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.switchOcrAssist) }
    private val tvResult: TextView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.tvResult) }
    private val imagePreview: ImageView by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.imagePreview) }
    private val progress: ProgressBar by lazy(LazyThreadSafetyMode.NONE) { findViewById(R.id.progress) }

    private var selectedBitmap: Bitmap? = null
    private var warmupJob: Job? = null
    private var selectionVersion: Int = 0

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                scope.launch {
                    try {
                        val bmp =
                            ImageUtils.imageToBitmap(
                                this@LlavaVisionActivity,
                                ImageSource.UriSource(uri),
                            )
                        val displayBmp =
                            ImageUtils.preprocessBitmap(
                                bmp,
                                maxDimension = IMAGE_MAX_DIMENSION,
                                enhance = false,
                            )
                        runOnUiThread {
                            applySelectedBitmap(displayBmp)
                            startWarmup()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load image for preview: ${e.message}")
                        runOnUiThread {
                            tvResult.text = "Error loading image: ${e.message}"
                        }
                    }
                }
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                val safeBmp =
                    ImageUtils.preprocessBitmap(
                        bitmap,
                        maxDimension = CAPTURE_MAX_DIMENSION,
                        enhance = false,
                    )
                applySelectedBitmap(safeBmp)
                startWarmup()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_llava_vision)

        btnPick.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        btnTake.setOnClickListener {
            takePictureLauncher.launch(null)
        }

        btnRun.setOnClickListener {
            runVisionQuery()
        }

        btnDescribeLocal.setOnClickListener {
            runLocalDescribe()
        }
    }

    private fun applySelectedBitmap(bitmap: Bitmap) {
        selectedBitmap = bitmap
        selectionVersion += 1
        imagePreview.setImageBitmap(bitmap)
        progress.visibility = View.GONE
        tvResult.text = "Image ready. Warming vision model in the background..."
    }

    private fun startWarmup() {
        val currentVersion = selectionVersion
        warmupJob?.cancel()
        warmupJob =
            scope.launch {
                try {
                    runOnUiThread {
                        progress.visibility = View.VISIBLE
                        tvResult.text = "Image ready. Warming vision model..."
                    }
                    edge.vision.prepare()
                    if (currentVersion != selectionVersion) return@launch
                    runOnUiThread {
                        progress.visibility = View.GONE
                        if (tvResult.text.isBlank() || tvResult.text.contains("Warming vision model")) {
                            tvResult.text = "Image ready. Vision model warmed."
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Vision warm-up failed", e)
                    if (currentVersion != selectionVersion) return@launch
                    runOnUiThread {
                        progress.visibility = View.GONE
                        tvResult.text = "Image ready. Model warm-up failed; it will load on the first question."
                    }
                }
            }
    }

    private fun runLocalDescribe() {
        val bitmap = selectedBitmap
        if (bitmap == null) {
            tvResult.text = "Pick or take an image first"
            return
        }

        progress.visibility = View.VISIBLE
        tvResult.text = ""

        val exceptionHandler =
            CoroutineExceptionHandler { _, ex ->
                Log.e(TAG, "Unhandled coroutine error", ex)
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = "Error: ${ex.message}"
                }
            }

        scope.launch(exceptionHandler) {
            try {
                val ocrText =
                    try {
                        edge.vision.extractText(bitmap)
                    } catch (e: Exception) {
                        Log.w(TAG, "OCR failed in local describe", e)
                        ""
                    }

                val desc =
                    LocalImageDescriber.describe(
                        this@LlavaVisionActivity,
                        ImageSource.BitmapSource(bitmap),
                    )

                runOnUiThread {
                    progress.visibility = View.GONE
                    val sb = StringBuilder()
                    sb.appendLine("Local description: ${desc.summary}")
                    if (desc.labels.isNotEmpty()) sb.appendLine("Labels: ${desc.labels.joinToString(", ")}")
                    val size = desc.size
                    if (size != null) sb.appendLine("Size: ${size.first}x${size.second}")
                    if (desc.dominantColor != null) sb.appendLine("Dominant color: ${desc.dominantColor}")
                    if (ocrText.isNotBlank()) {
                        sb.appendLine("OCR: ${ocrText.take(500)}")
                    }
                    tvResult.text = sb.toString().trim()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Local describe failed", e)
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = "Error: ${e.message}"
                }
            }
        }
    }

    private fun runVisionQuery() {
        val bitmap = selectedBitmap
        if (bitmap == null) {
            tvResult.text = "Pick or take an image first"
            return
        }

        val promptText = etPrompt.text.toString()
        val ocrAssistEnabled = switchOcrAssist.isChecked
        progress.visibility = View.VISIBLE
        tvResult.text = ""

        val exceptionHandler =
            CoroutineExceptionHandler { _, ex ->
                Log.e(TAG, "Unhandled coroutine error", ex)
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = "Error: ${ex.message}"
                }
            }

        scope.launch(exceptionHandler) {
            try {
                val activeWarmup = warmupJob
                if (activeWarmup?.isActive == true) {
                    runOnUiThread { tvResult.text = "Finishing vision warm-up..." }
                    joinAll(activeWarmup)
                }

                val ocrText =
                    if (ocrAssistEnabled) {
                        runOnUiThread { tvResult.text = "Running OCR assist..." }
                        try {
                            edge.vision.extractText(bitmap)
                        } catch (e: Exception) {
                            Log.w(TAG, "OCR failed", e)
                            ""
                        }
                    } else {
                        null
                    }

                val finalPrompt =
                    LlavaVisionPromptBuilder.buildModelPrompt(
                        userPrompt = promptText,
                        ocrAssistEnabled = ocrAssistEnabled,
                        ocrText = ocrText,
                    )

                val resultText =
                    edge.vision.analyze(bitmap, finalPrompt) { status ->
                        runOnUiThread {
                            tvResult.text = status
                        }
                    }

                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = resultText.trim().ifBlank { "Model returned an empty response." }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Vision demo failed", e)
                runOnUiThread {
                    progress.visibility = View.GONE
                    tvResult.text = "Error: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
